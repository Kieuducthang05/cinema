package vn.edu.uet.cinema.persistence.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import vn.edu.uet.cinema.TestcontainersConfiguration;
import vn.edu.uet.cinema.business.ConflictException;
import vn.edu.uet.cinema.business.NotFoundException;
import vn.edu.uet.cinema.business.account.AccountService;
import vn.edu.uet.cinema.business.auditorium.Auditorium;
import vn.edu.uet.cinema.business.auditorium.AuditoriumService;
import vn.edu.uet.cinema.business.booking.BookedSeat;
import vn.edu.uet.cinema.business.booking.Booking;
import vn.edu.uet.cinema.business.booking.BookingRepository;
import vn.edu.uet.cinema.business.booking.BookingService;
import vn.edu.uet.cinema.business.booking.BookingStatus;
import vn.edu.uet.cinema.business.booking.SeatsTakenException;
import vn.edu.uet.cinema.business.cinema.CinemaService;
import vn.edu.uet.cinema.business.movie.AgeRating;
import vn.edu.uet.cinema.business.movie.MovieService;
import vn.edu.uet.cinema.business.showtime.CancellationReason;
import vn.edu.uet.cinema.business.showtime.SeatOccupancyReader;
import vn.edu.uet.cinema.business.showtime.SeatStatus;
import vn.edu.uet.cinema.business.showtime.ShowtimeService;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BookingPersistenceTest {

	@Autowired BookingService bookings;
	@Autowired BookingRepository repository;
	@Autowired SeatOccupancyReader occupancy;
	@Autowired AccountService accounts;
	@Autowired MovieService movies;
	@Autowired CinemaService cinemas;
	@Autowired AuditoriumService auditoriums;
	@Autowired ShowtimeService showtimes;

	private final Instant now = Instant.now();

	@Test
	void concurrentBookingsOfTheSameSeatsInAnyOrderHaveExactlyOneWinnerAndNoOtherError() throws Exception {
		int threads = 10;
		Auditorium hall = auditorium();
		UUID showtimeId = showtime(hall);
		UUID a = hall.seats().get(0).id();
		UUID b = hall.seats().get(1).id();
		CountDownLatch start = new CountDownLatch(1);
		// Half ask for [a, b], half for [b, a]: opposite lock orders must not surface as a deadlock error.
		List<Callable<Boolean>> attempts = IntStream.range(0, threads).<Callable<Boolean>>mapToObj(i -> {
			UUID customerId = customer();
			List<UUID> seatIds = i % 2 == 0 ? List.of(a, b) : List.of(b, a);
			return () -> {
				start.await();
				try {
					bookings.book(customerId, showtimeId, seatIds, Instant.now());
					return true;
				} catch (SeatsTakenException seatsTaken) {
					assertThat(seatsTaken.seatIds()).containsExactlyElementsOf(seatIds);
					return false;
				}
			};
		}).toList();

		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			List<Future<Boolean>> results = attempts.stream().map(pool::submit).toList();
			start.countDown();
			long winners = 0;
			for (Future<Boolean> result : results) {
				winners += result.get() ? 1 : 0;
			}
			assertThat(winners).isEqualTo(1);
		} finally {
			pool.shutdownNow();
		}
		assertThat(occupancy.statuses(showtimeId, Instant.now()))
			.containsOnly(Map.entry(a, SeatStatus.HELD), Map.entry(b, SeatStatus.HELD));
	}

	@Test
	void seatOfALapsedHoldIsFreeAndItsBookingIsDeletedWhenSomeoneElseBooksIt() {
		Auditorium hall = auditorium();
		UUID showtimeId = showtime(hall);
		UUID seatId = hall.seats().get(0).id();
		UUID first = customer();
		Instant elevenMinutesAgo = now.minus(11, ChronoUnit.MINUTES);
		Booking lapsed = bookings.book(first, showtimeId, List.of(seatId), elevenMinutesAgo);

		assertThat(occupancy.statuses(showtimeId, now)).isEmpty();
		bookings.book(customer(), showtimeId, List.of(seatId), now);

		// Asked as of when it was still live: only a deleted Booking is missing then.
		assertThat(repository.findLivePending(first, elevenMinutesAgo)).as("lapsed Booking %s", lapsed.id())
			.isEmpty();
		assertThat(occupancy.statuses(showtimeId, now)).containsExactly(Map.entry(seatId, SeatStatus.HELD));
	}

	@Test
	void customersOwnLapsedPendingBookingDoesNotStopANewOne() {
		Auditorium hall = auditorium();
		UUID showtimeId = showtime(hall);
		UUID customerId = customer();
		bookings.book(customerId, showtimeId, List.of(hall.seats().get(0).id()), now.minus(11, ChronoUnit.MINUTES));

		Booking fresh = bookings.book(customerId, showtimeId, List.of(hall.seats().get(1).id()), now);

		assertThat(repository.findLivePending(customerId, now)).contains(fresh);
		assertThatThrownBy(() -> bookings.book(customerId, showtimeId, List.of(hall.seats().get(2).id()), now))
			.isInstanceOf(ConflictException.class).hasMessageContaining(fresh.id().toString());
	}

	@Test
	void seatMapShowsHeldForLiveHoldAndSoldForConfirmedBooking() {
		Auditorium hall = auditorium();
		UUID showtimeId = showtime(hall);
		UUID held = hall.seats().get(0).id();
		UUID sold = hall.seats().get(1).id();
		bookings.book(customer(), showtimeId, List.of(held), now);
		repository.saveNew(new Booking(UUID.randomUUID(), customer(), showtimeId, BookingStatus.CONFIRMED,
			now.minus(1, ChronoUnit.HOURS), now.minus(50, ChronoUnit.MINUTES), List.of(new BookedSeat(sold, 100_000))),
			now);

		assertThat(occupancy.statuses(showtimeId, now))
			.containsOnly(Map.entry(held, SeatStatus.HELD), Map.entry(sold, SeatStatus.SOLD));
	}

	@Test
	void showtimeWithLiveHoldCannotBeDeletedButLapsedHoldsGoWithIt() {
		Auditorium hall = auditorium();
		UUID showtimeId = showtime(hall);
		bookings.book(customer(), showtimeId, List.of(hall.seats().get(0).id()), now.minus(11, ChronoUnit.MINUTES));
		bookings.book(customer(), showtimeId, List.of(hall.seats().get(1).id()), now);

		assertThatThrownBy(() -> showtimes.delete(showtimeId, now)).isInstanceOf(ConflictException.class);

		Auditorium otherHall = auditorium();
		UUID lapsedOnlyId = showtime(otherHall);
		bookings.book(customer(), lapsedOnlyId, List.of(otherHall.seats().get(0).id()), now.minus(11, ChronoUnit.MINUTES));
		showtimes.delete(lapsedOnlyId, now);
		assertThatThrownBy(() -> showtimes.get(lapsedOnlyId)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void paymentIsAConditionalUpdateThatOnlyALiveHoldPasses() {
		Auditorium hall = auditorium();
		UUID showtimeId = showtime(hall);
		UUID seatId = hall.seats().get(0).id();
		UUID customerId = customer();
		Booking lapsed = bookings.book(customerId, showtimeId, List.of(hall.seats().get(1).id()),
			now.minus(11, ChronoUnit.MINUTES));
		assertThat(repository.confirm(lapsed.id(), now)).isFalse();

		Booking live = bookings.book(customerId, showtimeId, List.of(seatId), now);

		assertThat(repository.confirm(live.id(), now)).isTrue();
		assertThat(repository.confirm(live.id(), now)).isFalse();
		assertThat(repository.deletePending(live.id())).isFalse();
		assertThat(repository.findById(live.id())).get().extracting(Booking::status).isEqualTo(BookingStatus.CONFIRMED);
		assertThat(occupancy.statuses(showtimeId, now)).containsExactly(Map.entry(seatId, SeatStatus.SOLD));
	}

	@Test
	void abandonedBookingIsGoneAndItsSeatsAreFree() {
		Auditorium hall = auditorium();
		UUID showtimeId = showtime(hall);
		UUID seatId = hall.seats().get(0).id();
		Booking abandoned = bookings.book(customer(), showtimeId, List.of(seatId), now);

		assertThat(repository.deletePending(abandoned.id())).isTrue();

		assertThat(repository.findById(abandoned.id())).isEmpty();
		assertThat(occupancy.statuses(showtimeId, now)).isEmpty();
		bookings.book(customer(), showtimeId, List.of(seatId), now);
	}

	@Test
	void customersBookingsAreListedNewestFirst() {
		Auditorium hall = auditorium();
		UUID showtimeId = showtime(hall);
		UUID customerId = customer();
		Instant earlier = now.minus(20, ChronoUnit.MINUTES);
		Booking paid = bookings.book(customerId, showtimeId, List.of(hall.seats().get(0).id()), earlier);
		repository.confirm(paid.id(), earlier);
		Booking pending = bookings.book(customerId, showtimeId, List.of(hall.seats().get(1).id()), now);
		bookings.book(customer(), showtimeId, List.of(hall.seats().get(2).id()), now);

		assertThat(repository.findByCustomer(customerId)).extracting(Booking::id)
			.containsExactly(pending.id(), paid.id());
	}

	@Test
	void cancellingAShowtimeDeletesItsPendingBookingsIncludingExpiredOnes() {
		Auditorium hall = auditorium();
		UUID showtimeId = showtime(hall);
		Booking live = bookings.book(customer(), showtimeId, List.of(hall.seats().get(0).id()), now);
		Booking expired = bookings.book(customer(), showtimeId, List.of(hall.seats().get(1).id()),
			now.minus(11, ChronoUnit.MINUTES));

		showtimes.cancel(showtimeId, CancellationReason.EQUIPMENT_FAILURE, null, now);

		assertThat(repository.findById(live.id())).isEmpty();
		assertThat(repository.findById(expired.id())).isEmpty();
		assertThat(occupancy.statuses(showtimeId, now)).isEmpty();
	}

	@Test
	void cancellingAShowtimeRefundsItsConfirmedBookings() {
		Auditorium hall = auditorium();
		UUID showtimeId = showtime(hall);
		Booking paid = bookings.book(customer(), showtimeId, List.of(hall.seats().get(0).id()), now);
		bookings.pay(paid.customerId(), paid.id(), now);

		showtimes.cancel(showtimeId, CancellationReason.POWER_OUTAGE, null, now);

		assertThat(bookings.get(paid.customerId(), paid.id()).status()).isEqualTo(BookingStatus.REFUNDED);
		assertThat(bookings.get(paid.customerId(), paid.id()).totalPrice()).isEqualTo(paid.totalPrice());
	}

	@Test
	void persistenceRefusesABookingOnACancelledShowtimeEvenWhenServiceGuardWasBypassed() {
		Auditorium hall = auditorium();
		UUID showtimeId = showtime(hall);
		showtimes.cancel(showtimeId, CancellationReason.EMERGENCY, null, now);
		Booking late = new Booking(UUID.randomUUID(), customer(), showtimeId, BookingStatus.PENDING, now,
			now.plus(10, ChronoUnit.MINUTES), List.of(new BookedSeat(hall.seats().get(0).id(), 100_000)));

		assertThatThrownBy(() -> repository.saveNew(late, now)).isInstanceOf(ConflictException.class);
		assertThat(repository.findById(late.id())).isEmpty();
	}

	@Test
	void noPendingOrConfirmedBookingOutlivesACancelWhateverTheInterleaving() throws Exception {
		for (int round = 0; round < 5; round++) {
			UUID cinemaId = cinemas.create("Cinema " + UUID.randomUUID(), "Address").id();
			Auditorium hall = auditoriums.create(cinemaId, "Hall", 4, 4, List.of(), List.of());
			UUID showtimeId = showtime(hall);
			List<UUID> customers = hall.seats().stream().map(seat -> customer()).toList();
			CountDownLatch start = new CountDownLatch(1);
			List<Callable<Void>> work = new java.util.ArrayList<>();
			for (int i = 0; i < customers.size(); i++) {
				UUID customerId = customers.get(i);
				UUID seatId = hall.seats().get(i).id();
				work.add(() -> {
					start.await();
					try {
						Booking booking = bookings.book(customerId, showtimeId, List.of(seatId), Instant.now());
						bookings.pay(customerId, booking.id(), Instant.now());
					} catch (ConflictException | NotFoundException cancelledMeanwhile) {
						// Booking a Cancelled Showtime, or paying a Booking the cancel deleted or refunded.
					}
					return null;
				});
			}
			work.add(() -> {
				start.await();
				// Land somewhere among the bookings and payments rather than always before them.
				Thread.sleep(ThreadLocalRandom.current().nextInt(40));
				showtimes.cancel(showtimeId, CancellationReason.EMERGENCY, null, Instant.now());
				return null;
			});

			ExecutorService pool = Executors.newFixedThreadPool(work.size());
			try {
				List<Future<Void>> results = work.stream().map(pool::submit).toList();
				start.countDown();
				for (Future<Void> result : results) {
					result.get();
				}
			} finally {
				pool.shutdownNow();
			}
			List<BookingStatus> left = customers.stream().flatMap(customerId -> bookings.list(customerId).stream())
				.map(Booking::status).toList();
			assertThat(left).as("round %d", round).doesNotContain(BookingStatus.PENDING, BookingStatus.CONFIRMED);
		}
	}

	private UUID customer() {
		return accounts.registerCustomer("booking-" + UUID.randomUUID() + "@example.com", "password123", "Customer")
			.id();
	}

	private Auditorium auditorium() {
		UUID cinemaId = cinemas.create("Cinema " + UUID.randomUUID(), "Address").id();
		return auditoriums.create(cinemaId, "Hall", 2, 2, List.of(), List.of());
	}

	private UUID showtime(Auditorium hall) {
		UUID movieId = movies.create("Film", "Synopsis", 90, "Drama", AgeRating.P, LocalDate.parse("2026-09-01"),
			"poster").id();
		return showtimes.create(movieId, hall.id(), now.plus(1, ChronoUnit.DAYS), 100_000).id();
	}
}
