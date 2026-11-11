package vn.edu.uet.cinema.business.showtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.edu.uet.cinema.business.ConflictException;
import vn.edu.uet.cinema.business.NotFoundException;
import vn.edu.uet.cinema.business.auditorium.Auditorium;
import vn.edu.uet.cinema.business.auditorium.AuditoriumService;
import vn.edu.uet.cinema.business.auditorium.InMemoryAuditoriums;
import vn.edu.uet.cinema.business.auditorium.Seat;
import vn.edu.uet.cinema.business.auditorium.SeatType;
import vn.edu.uet.cinema.business.booking.BookedSeat;
import vn.edu.uet.cinema.business.booking.Booking;
import vn.edu.uet.cinema.business.booking.BookingStatus;
import vn.edu.uet.cinema.business.booking.InMemoryBookings;
import vn.edu.uet.cinema.business.booking.InMemorySeatOccupancy;
import vn.edu.uet.cinema.business.cinema.CinemaService;
import vn.edu.uet.cinema.business.cinema.InMemoryCinemas;
import vn.edu.uet.cinema.business.movie.AgeRating;
import vn.edu.uet.cinema.business.movie.InMemoryMovies;
import vn.edu.uet.cinema.business.movie.Movie;
import vn.edu.uet.cinema.business.movie.MovieService;

class ShowtimeServiceTest {

	private static final Instant STARTS_AT = Instant.parse("2026-10-01T14:00:00Z");

	private final InMemoryAuditoriums auditoriumRows = new InMemoryAuditoriums();
	private final InMemoryShowtimes repository = new InMemoryShowtimes(auditoriumRows);
	private final InMemoryMovies movieRows = new InMemoryMovies();
	private final MovieService movies = new MovieService(movieRows, repository);
	private final AuditoriumService auditoriums = new AuditoriumService(
		new CinemaService(new InMemoryCinemas(), auditoriumRows), auditoriumRows, repository);
	private final ShowtimeService service = new ShowtimeService(movies, auditoriums, repository,
		new InMemorySeatOccupancy(new InMemoryBookings(repository)));

	private final UUID auditoriumId = auditoriumRows.save(new Auditorium(UUID.randomUUID(), UUID.randomUUID(), "Hall",
		1, 1, List.of())).id();

	@Test
	void rejectsNonPositiveBasePrice() {
		assertThatThrownBy(() -> service.create(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), 0))
			.isInstanceOf(InvalidShowtimeException.class);
	}

	@Test
	void creationSnapshotsMovieDurationPlusCleaningTime() {
		UUID movieId = movie(120);

		Showtime showtime = service.create(movieId, auditoriumId, STARTS_AT, 100_000L);

		assertThat(showtime.startsAt()).isEqualTo(STARTS_AT);
		assertThat(showtime.endsAt()).isEqualTo(Instant.parse("2026-10-01T16:15:00Z"));
		assertThat(showtime.basePrice()).isEqualTo(100_000L);
		assertThat(showtime.movieId()).isEqualTo(movieId);
		assertThat(showtime.auditoriumId()).isEqualTo(auditoriumId);
		assertThat(service.get(showtime.id())).isEqualTo(showtime);
	}

	@Test
	void overlappingShowtimeInSameAuditoriumIsRejected() {
		UUID movieId = movie(120);
		service.create(movieId, auditoriumId, STARTS_AT, 100_000);

		assertThatThrownBy(() -> service.create(movieId, auditoriumId, STARTS_AT.plusSeconds(134 * 60), 100_000))
			.isInstanceOf(ConflictException.class);
		service.create(movieId, auditoriumId, STARTS_AT.plusSeconds(135 * 60), 100_000);
	}

	@Test
	void durationArithmeticDoesNotOverflow() {
		UUID movieId = movie(Integer.MAX_VALUE);

		assertThat(service.create(movieId, auditoriumId, STARTS_AT, 100_000L).endsAt())
			.isEqualTo(STARTS_AT.plusSeconds(((long) Integer.MAX_VALUE + 15) * 60));
	}

	@Test
	void cancellingReturnsTheCancelledShowtimeAndSettlesItsBookings() {
		Showtime scheduled = scheduled();
		Instant now = Instant.parse("2026-10-01T15:00:00Z");
		Booking pending = booking(scheduled, BookingStatus.PENDING, now.plusSeconds(60));
		Booking confirmed = booking(scheduled, BookingStatus.CONFIRMED, now.minusSeconds(60));

		Showtime cancelled = service.cancel(scheduled.id(), CancellationReason.EMERGENCY, "Fire alarm", now);

		assertThat(cancelled).isEqualTo(new Showtime(scheduled.id(), scheduled.movieId(), scheduled.auditoriumId(),
			scheduled.startsAt(), scheduled.endsAt(), scheduled.basePrice(), ShowtimeStatus.CANCELLED,
			new Cancellation(CancellationReason.EMERGENCY, "Fire alarm")));
		assertThat(service.get(scheduled.id())).isEqualTo(cancelled);
		assertThat(repository.bookingRows).doesNotContainKey(pending.id());
		assertThat(repository.bookingRows.get(confirmed.id()).status()).isEqualTo(BookingStatus.REFUNDED);
	}

	@Test
	void unknownShowtimeCannotBeCancelled() {
		assertThatThrownBy(() -> service.cancel(UUID.randomUUID(), CancellationReason.EMERGENCY, null, Instant.now()))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void endedOrAlreadyCancelledShowtimeCannotBeCancelled() {
		Showtime ended = scheduled();
		Showtime twice = scheduled();
		Instant now = Instant.parse("2026-10-01T13:00:00Z");
		service.cancel(twice.id(), CancellationReason.EMERGENCY, null, now);

		assertThatThrownBy(() -> service.cancel(ended.id(), CancellationReason.POWER_OUTAGE, null, ended.endsAt()))
			.isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> service.cancel(twice.id(), CancellationReason.POWER_OUTAGE, null, now))
			.isInstanceOf(ConflictException.class);
	}

	@Test
	void otherNeedsANoteAndABlankNoteIsNoNote() {
		Showtime showtime = scheduled();
		Instant now = Instant.parse("2026-10-01T13:00:00Z");

		for (String missing : new String[] {null, "", "   "}) {
			assertThatThrownBy(() -> service.cancel(showtime.id(), CancellationReason.OTHER, missing, now))
				.as("note %s", missing).isInstanceOf(InvalidShowtimeException.class);
		}
		assertThat(service.cancel(showtime.id(), CancellationReason.EQUIPMENT_FAILURE, "  ", now).cancellation())
			.isEqualTo(new Cancellation(CancellationReason.EQUIPMENT_FAILURE, null));
	}

	@Test
	void noteIsAtMostTwoHundredCharacters() {
		Showtime showtime = scheduled();
		Instant now = Instant.parse("2026-10-01T13:00:00Z");

		assertThatThrownBy(() -> service.cancel(showtime.id(), CancellationReason.OTHER, "x".repeat(201), now))
			.isInstanceOf(InvalidShowtimeException.class);
		assertThat(service.cancel(showtime.id(), CancellationReason.OTHER, "x".repeat(200), now).cancellation().note())
			.hasSize(200);
	}

	@Test
	void deletingAtAFixedTimeRemovesLapsedHoldsButALiveHoldBlocks() {
		Instant now = Instant.parse("2026-10-01T13:00:00Z");
		Showtime lapsedOnly = scheduled();
		Booking lapsed = booking(lapsedOnly, BookingStatus.PENDING, now.minusSeconds(1));
		Showtime held = scheduled();
		booking(held, BookingStatus.PENDING, now.plusSeconds(1));

		service.delete(lapsedOnly.id(), now);

		assertThatThrownBy(() -> service.get(lapsedOnly.id())).isInstanceOf(NotFoundException.class);
		assertThat(repository.bookingRows).doesNotContainKey(lapsed.id());
		assertThatThrownBy(() -> service.delete(held.id(), now)).isInstanceOf(ConflictException.class);
		assertThat(service.get(held.id())).isEqualTo(held);
	}

	@Test
	void pricedSeatsPriceEachSeatTypeFromTheBasePrice() {
		Showtime showtime = withSeats();

		assertThat(service.pricedSeats(showtime)).containsExactly(
			Map.entry(standard.id(), new PricedSeat(standard, 100_000)), Map.entry(vip.id(), new PricedSeat(vip, 120_000)),
			Map.entry(couple.id(), new PricedSeat(couple, 210_000)), Map.entry(lapsed.id(), new PricedSeat(lapsed, 100_000)));
	}

	@Test
	void seatMapShowsPriceAndStatusOfEverySeat() {
		Showtime showtime = withSeats();
		Instant now = Instant.parse("2026-10-01T13:00:00Z");
		booking(showtime, BookingStatus.PENDING, now.plusSeconds(1), standard);
		booking(showtime, BookingStatus.CONFIRMED, now.minusSeconds(1), vip);
		booking(showtime, BookingStatus.PENDING, now, couple);
		booking(showtime, BookingStatus.PENDING, now.minusSeconds(1), lapsed);

		assertThat(service.seatMap(showtime.id(), now)).containsExactly(
			new SeatMapEntry(new PricedSeat(standard, 100_000), SeatStatus.HELD),
			new SeatMapEntry(new PricedSeat(vip, 120_000), SeatStatus.SOLD),
			new SeatMapEntry(new PricedSeat(couple, 210_000), SeatStatus.AVAILABLE),
			new SeatMapEntry(new PricedSeat(lapsed, 100_000), SeatStatus.AVAILABLE));
	}

	@Test
	void seatMapOfAnUnknownShowtimeIsNotFoundAndOfACancelledOneAConflict() {
		Showtime cancelled = withSeats();
		Instant now = Instant.parse("2026-10-01T13:00:00Z");
		service.cancel(cancelled.id(), CancellationReason.EMERGENCY, null, now);

		assertThatThrownBy(() -> service.seatMap(UUID.randomUUID(), now)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.seatMap(cancelled.id(), now)).isInstanceOf(ConflictException.class)
			.hasMessage("Showtime is Cancelled");
	}

	private final Seat standard = new Seat(UUID.randomUUID(), "A", 1, SeatType.STANDARD);
	private final Seat vip = new Seat(UUID.randomUUID(), "B", 1, SeatType.VIP);
	private final Seat couple = new Seat(UUID.randomUUID(), "C", 1, SeatType.COUPLE);
	private final Seat lapsed = new Seat(UUID.randomUUID(), "D", 1, SeatType.STANDARD);

	private Showtime withSeats() {
		Auditorium auditorium = auditoriumRows.save(new Auditorium(UUID.randomUUID(), UUID.randomUUID(), "Seated", 4, 1,
			List.of(standard, vip, couple, lapsed)));
		return repository.save(new Showtime(UUID.randomUUID(), UUID.randomUUID(), auditorium.id(), STARTS_AT,
			Instant.parse("2026-10-01T16:15:00Z"), 100_000L));
	}

	private void booking(Showtime showtime, BookingStatus status, Instant expiresAt, Seat seat) {
		Booking booking = new Booking(UUID.randomUUID(), UUID.randomUUID(), showtime.id(), status,
			expiresAt.minusSeconds(600), expiresAt, List.of(new BookedSeat(seat.id(), 100_000)));
		repository.bookingRows.put(booking.id(), booking);
	}

	private UUID movie(int durationMinutes) {
		return movieRows.save(new Movie(UUID.randomUUID(), "Film", "Synopsis", durationMinutes, "Drama", AgeRating.P,
			LocalDate.parse("2026-09-01"), "poster")).id();
	}

	private Showtime scheduled() {
		return repository.save(new Showtime(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), STARTS_AT,
			Instant.parse("2026-10-01T16:15:00Z"), 100_000L));
	}

	private Booking booking(Showtime showtime, BookingStatus status, Instant expiresAt) {
		Booking booking = new Booking(UUID.randomUUID(), UUID.randomUUID(), showtime.id(), status,
			expiresAt.minusSeconds(600), expiresAt, List.of(new BookedSeat(UUID.randomUUID(), 100_000)));
		repository.bookingRows.put(booking.id(), booking);
		return booking;
	}
}
