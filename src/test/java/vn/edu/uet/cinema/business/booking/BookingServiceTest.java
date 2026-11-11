package vn.edu.uet.cinema.business.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import vn.edu.uet.cinema.business.ConflictException;
import vn.edu.uet.cinema.business.NotFoundException;
import vn.edu.uet.cinema.business.auditorium.Auditorium;
import vn.edu.uet.cinema.business.auditorium.AuditoriumService;
import vn.edu.uet.cinema.business.auditorium.InMemoryAuditoriums;
import vn.edu.uet.cinema.business.auditorium.Seat;
import vn.edu.uet.cinema.business.auditorium.SeatType;
import vn.edu.uet.cinema.business.cinema.CinemaService;
import vn.edu.uet.cinema.business.cinema.InMemoryCinemas;
import vn.edu.uet.cinema.business.movie.InMemoryMovies;
import vn.edu.uet.cinema.business.movie.MovieService;
import vn.edu.uet.cinema.business.showtime.CancellationReason;
import vn.edu.uet.cinema.business.showtime.InMemoryShowtimes;
import vn.edu.uet.cinema.business.showtime.SeatStatus;
import vn.edu.uet.cinema.business.showtime.Showtime;
import vn.edu.uet.cinema.business.showtime.ShowtimeService;

class BookingServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

	private final InMemoryAuditoriums auditoriumRows = new InMemoryAuditoriums();
	private final InMemoryShowtimes showtimeRows = new InMemoryShowtimes(auditoriumRows);
	private final InMemoryBookings repository = new InMemoryBookings(showtimeRows);
	private final AuditoriumService auditoriums = new AuditoriumService(
		new CinemaService(new InMemoryCinemas(), auditoriumRows), auditoriumRows, showtimeRows);
	private final ShowtimeService showtimes = new ShowtimeService(new MovieService(new InMemoryMovies(), showtimeRows),
		auditoriums, showtimeRows, new InMemorySeatOccupancy(repository));
	private final BookingService service = new BookingService(showtimes, repository);

	private final UUID customerId = UUID.randomUUID();
	private final Seat standard = new Seat(UUID.randomUUID(), "A", 1, SeatType.STANDARD);
	private final Seat vip = new Seat(UUID.randomUUID(), "B", 1, SeatType.VIP);
	private final Seat couple = new Seat(UUID.randomUUID(), "C", 1, SeatType.COUPLE);
	private final Showtime showtime = showtime(Instant.parse("2026-10-01T12:00:00Z"));

	@Test
	void newBookingIsPendingWithTenMinuteHoldAndCopiedSeatPrices() {
		Booking booking = service.book(customerId, showtime.id(), List.of(standard.id(), vip.id(), couple.id()), NOW);

		assertThat(booking.customerId()).isEqualTo(customerId);
		assertThat(booking.showtimeId()).isEqualTo(showtime.id());
		assertThat(booking.status()).isEqualTo(BookingStatus.PENDING);
		assertThat(booking.expiresAt()).isEqualTo(Instant.parse("2026-10-01T10:10:00Z"));
		assertThat(booking.seats()).containsExactly(new BookedSeat(standard.id(), 100_000),
			new BookedSeat(vip.id(), 120_000), new BookedSeat(couple.id(), 210_000));
		assertThat(booking.totalPrice()).isEqualTo(430_000);
		assertThat(service.get(customerId, booking.id())).isEqualTo(booking);
	}

	@Test
	void seatsMustBeOneToEightDistinctSeatsOfTheShowtimesAuditorium() {
		List<UUID> nine = Stream.generate(UUID::randomUUID).limit(9).toList();
		for (List<UUID> seatIds : List.of(List.<UUID>of(), nine, List.of(standard.id(), standard.id()),
			List.of(standard.id(), UUID.randomUUID()))) {
			assertThatThrownBy(() -> service.book(customerId, showtime.id(), seatIds, NOW))
				.as("%s", seatIds).isInstanceOf(InvalidBookingException.class);
		}
		assertThat(service.list(customerId)).isEmpty();
	}

	@Test
	void showtimeThatHasStartedCannotBeBooked() {
		Showtime started = showtime(NOW);

		assertThatThrownBy(() -> service.book(customerId, started.id(), List.of(standard.id()), NOW))
			.isInstanceOf(ConflictException.class);
		assertThat(service.list(customerId)).isEmpty();
	}

	@Test
	void cancelledShowtimeCannotBeBooked() {
		showtimes.cancel(showtime.id(), CancellationReason.EMERGENCY, null, NOW);

		assertThatThrownBy(() -> service.book(customerId, showtime.id(), List.of(standard.id()), NOW))
			.isInstanceOf(ConflictException.class).hasMessageContaining("Cancelled");
		assertThat(service.list(customerId)).isEmpty();
	}

	@Test
	void eachRequestGetsTheErrorOfTheFirstRuleItBreaks() {
		UUID outside = UUID.randomUUID();
		Showtime cancelledAndStarted = showtime(NOW);
		showtimes.cancel(cancelledAndStarted.id(), CancellationReason.EMERGENCY, null, NOW.minusSeconds(1));
		Showtime started = showtime(NOW);

		assertThatThrownBy(() -> service.book(customerId, UUID.randomUUID(), List.of(outside, outside), NOW))
			.isInstanceOf(InvalidBookingException.class).hasMessage("Duplicate seatIds");
		assertThatThrownBy(() -> service.book(customerId, UUID.randomUUID(), List.of(outside), NOW))
			.isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.book(customerId, cancelledAndStarted.id(), List.of(outside), NOW))
			.isInstanceOf(ConflictException.class).hasMessage("Showtime is Cancelled");
		assertThatThrownBy(() -> service.book(customerId, started.id(), List.of(outside), NOW))
			.isInstanceOf(ConflictException.class).hasMessage("Showtime has already started");
		assertThatThrownBy(() -> service.book(customerId, showtime.id(), List.of(outside), NOW))
			.isInstanceOf(InvalidBookingException.class).hasMessageContaining("not in the Showtime's Auditorium");
	}

	@Test
	void customerWithLivePendingBookingIsToldItsId() {
		Booking pending = service.book(customerId, showtime.id(), List.of(vip.id()), NOW);

		assertThatThrownBy(() -> service.book(customerId, showtime.id(), List.of(standard.id()), NOW))
			.isInstanceOf(ConflictException.class).hasMessageContaining(pending.id().toString());
	}

	@Test
	void customerWhoseHoldLapsedCanBookAgain() {
		Booking lapsed = service.book(customerId, showtime.id(), List.of(vip.id()), NOW);
		Instant later = lapsed.expiresAt();

		Booking again = service.book(customerId, showtime.id(), List.of(standard.id()), later);

		assertThat(service.list(customerId)).containsExactly(again);
	}

	@Test
	void seatsHeldOrSoldAreTakenAndNamed() {
		Booking held = service.book(UUID.randomUUID(), showtime.id(), List.of(standard.id()), NOW);
		Booking sold = service.book(UUID.randomUUID(), showtime.id(), List.of(vip.id()), NOW);
		service.pay(sold.customerId(), sold.id(), NOW);

		assertThatThrownBy(() -> service.book(customerId, showtime.id(),
			List.of(standard.id(), vip.id(), couple.id()), NOW))
			.isInstanceOfSatisfying(SeatsTakenException.class,
				e -> assertThat(e.seatIds()).containsExactly(standard.id(), vip.id()));
		assertThat(held.status()).isEqualTo(BookingStatus.PENDING);
	}

	@Test
	void seatWhoseHoldLapsedIsFreeAgainAndTheLapsedHoldIsRemoved() {
		Booking lapsed = service.book(UUID.randomUUID(), showtime.id(), List.of(standard.id()), NOW);
		Instant later = lapsed.expiresAt();

		service.book(customerId, showtime.id(), List.of(standard.id()), later);

		assertThat(repository.findById(lapsed.id())).isEmpty();
	}

	@Test
	void occupancyShowsLiveHoldsAsHeldPaidSeatsAsSoldAndLapsedHoldsNotAtAll() {
		Booking held = service.book(UUID.randomUUID(), showtime.id(), List.of(standard.id()), NOW);
		Booking sold = service.book(UUID.randomUUID(), showtime.id(), List.of(vip.id()), NOW);
		service.pay(sold.customerId(), sold.id(), NOW);
		InMemorySeatOccupancy occupancy = new InMemorySeatOccupancy(repository);

		assertThat(occupancy.statuses(showtime.id(), NOW))
			.isEqualTo(Map.of(standard.id(), SeatStatus.HELD, vip.id(), SeatStatus.SOLD));
		assertThat(occupancy.statuses(showtime.id(), held.expiresAt()))
			.isEqualTo(Map.of(vip.id(), SeatStatus.SOLD));
	}

	@Test
	void customerOnlySeesTheirOwnBookings() {
		Booking own = stored(BookingStatus.PENDING, NOW.plusSeconds(60));

		assertThat(service.get(customerId, own.id())).isEqualTo(own);
		assertThatThrownBy(() -> service.get(UUID.randomUUID(), own.id())).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.get(customerId, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
	}

	@Test
	void pendingBookingPastItsHoldIsExpired() {
		Booking booking = stored(BookingStatus.PENDING, NOW);

		assertThat(booking.statusAt(NOW.minusSeconds(1))).isEqualTo(BookingStatus.PENDING);
		assertThat(booking.statusAt(NOW)).isEqualTo(BookingStatus.EXPIRED);
		assertThat(stored(BookingStatus.CONFIRMED, NOW).statusAt(NOW)).isEqualTo(BookingStatus.CONFIRMED);
	}

	@Test
	void paymentConfirmsALiveHold() {
		Booking pending = stored(BookingStatus.PENDING, NOW.plusSeconds(1));

		Booking paid = service.pay(customerId, pending.id(), NOW);

		assertThat(paid.status()).isEqualTo(BookingStatus.CONFIRMED);
		assertThat(paid.seats()).isEqualTo(pending.seats());
		assertThat(service.get(customerId, pending.id())).isEqualTo(paid);
	}

	@Test
	void onlyTheHoldDecidesSoPaymentAfterTheShowtimeStartedIsAllowed() {
		Instant afterStart = showtime.startsAt().plusSeconds(60);
		Booking pending = stored(BookingStatus.PENDING, afterStart.plusSeconds(1));

		assertThat(service.pay(customerId, pending.id(), afterStart).status()).isEqualTo(BookingStatus.CONFIRMED);
	}

	@Test
	void paymentOfAnExpiredBookingIsConflictAndLeavesItUnpaid() {
		Booking expired = stored(BookingStatus.PENDING, NOW);

		assertThatThrownBy(() -> service.pay(customerId, expired.id(), NOW)).isInstanceOf(ConflictException.class);
		assertThat(service.get(customerId, expired.id())).isEqualTo(expired);
	}

	@Test
	void paymentOfABookingWhoseHoldLapsesDuringPaymentIsConflict() {
		Booking pending = stored(BookingStatus.PENDING, NOW.plusSeconds(60));
		InMemoryBookings lapsing = new InMemoryBookings(showtimeRows) {
			@Override
			public boolean confirm(UUID id, Instant now) {
				return super.confirm(id, pending.expiresAt());
			}
		};
		BookingService racing = new BookingService(showtimes, lapsing);

		assertThatThrownBy(() -> racing.pay(customerId, pending.id(), pending.expiresAt().minusSeconds(1)))
			.isInstanceOf(ConflictException.class);
	}

	@Test
	void payingAConfirmedBookingReturnsItUnchanged() {
		Booking confirmed = stored(BookingStatus.CONFIRMED, NOW.plusSeconds(60));

		assertThat(service.pay(customerId, confirmed.id(), NOW)).isEqualTo(confirmed);
	}

	@Test
	void payingTwiceAtOnceReturnsTheConfirmedBookingToTheLoserToo() {
		Booking pending = stored(BookingStatus.PENDING, NOW.plusSeconds(60));
		// The other payment confirms it between our read and our conditional update.
		InMemoryBookings racing = new InMemoryBookings(showtimeRows) {
			@Override
			public boolean confirm(UUID id, Instant now) {
				super.confirm(id, now);
				return false;
			}
		};

		assertThat(new BookingService(showtimes, racing).pay(customerId, pending.id(), NOW))
			.isEqualTo(pending.confirmed());
	}

	@Test
	void pendingBookingCanBeAbandonedButConfirmedOneCannot() {
		Booking pending = stored(BookingStatus.PENDING, NOW.plusSeconds(60));
		Booking confirmed = stored(BookingStatus.CONFIRMED, NOW.plusSeconds(60));

		service.abandon(customerId, pending.id());

		assertThatThrownBy(() -> service.get(customerId, pending.id())).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.abandon(customerId, confirmed.id())).isInstanceOf(ConflictException.class);
		assertThat(service.get(customerId, confirmed.id())).isEqualTo(confirmed);
	}

	@Test
	void refundedBookingCannotBeAbandonedAndIsToldSo() {
		Booking refunded = stored(BookingStatus.REFUNDED, NOW.plusSeconds(60));

		assertThatThrownBy(() -> service.abandon(customerId, refunded.id()))
			.isInstanceOf(ConflictException.class).hasMessageContaining("REFUNDED");
		assertThat(service.get(customerId, refunded.id())).isEqualTo(refunded);
	}

	@Test
	void abandoningABookingThatVanishedMeanwhileIsNotFound() {
		Booking pending = stored(BookingStatus.PENDING, NOW.plusSeconds(60));
		// Someone else deletes it between our read and our delete.
		InMemoryBookings racing = new InMemoryBookings(showtimeRows) {
			@Override
			public boolean deletePending(UUID id) {
				super.deletePending(id);
				return false;
			}
		};

		assertThatThrownBy(() -> new BookingService(showtimes, racing).abandon(customerId, pending.id()))
			.isInstanceOf(NotFoundException.class);
	}

	private Booking stored(BookingStatus status, Instant expiresAt) {
		Booking booking = new Booking(UUID.randomUUID(), customerId, showtime.id(), status, expiresAt.minusSeconds(600),
			expiresAt, List.of(new BookedSeat(standard.id(), 100_000)));
		repository.rows.put(booking.id(), booking);
		return booking;
	}

	private Showtime showtime(Instant startsAt) {
		Auditorium auditorium = auditoriumRows.save(new Auditorium(UUID.randomUUID(), UUID.randomUUID(), "Hall", 3, 1,
			List.of(standard, vip, couple)));
		return showtimeRows.save(new Showtime(UUID.randomUUID(), UUID.randomUUID(), auditorium.id(), startsAt,
			startsAt.plusSeconds(7_200), 100_000));
	}
}
