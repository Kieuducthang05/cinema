package vn.edu.uet.cinema.business.showtime;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.edu.uet.cinema.business.ConflictException;
import vn.edu.uet.cinema.business.auditorium.Auditorium;
import vn.edu.uet.cinema.business.auditorium.InMemoryAuditoriums;
import vn.edu.uet.cinema.business.booking.Booking;
import vn.edu.uet.cinema.business.booking.BookingStatus;

/**
 * In-memory {@link ShowtimeRepository} for business tests. It owns the Booking rows too, because cancelling and
 * deleting a Showtime change them in the database: the Booking adapter is built on top of this one.
 */
public class InMemoryShowtimes implements ShowtimeRepository {

	private final Map<UUID, Showtime> rows = new LinkedHashMap<>();
	/** Booking rows by id, shared with the Booking adapter and the seat occupancy reader. */
	public final Map<UUID, Booking> bookingRows = new LinkedHashMap<>();
	private final InMemoryAuditoriums auditoriums;

	public InMemoryShowtimes(InMemoryAuditoriums auditoriums) {
		this.auditoriums = auditoriums;
	}

	@Override
	public Showtime save(Showtime showtime) {
		rows.put(showtime.id(), showtime);
		return showtime;
	}

	@Override
	public Optional<Showtime> findById(UUID id) {
		return Optional.ofNullable(rows.get(id));
	}

	@Override
	public void deleteById(UUID id, Instant now) {
		bookingRows.values().removeIf(b -> b.showtimeId().equals(id) && b.statusAt(now) == BookingStatus.EXPIRED);
		if (bookingRows.values().stream().anyMatch(b -> b.showtimeId().equals(id))) {
			throw new ConflictException("Showtime has Bookings");
		}
		rows.remove(id);
	}

	@Override
	public boolean cancel(UUID id, Cancellation cancellation, Instant now) {
		Showtime s = rows.get(id);
		if (s == null || s.status() != ShowtimeStatus.SCHEDULED || !s.endsAt().isAfter(now)) {
			return false;
		}
		rows.put(id, new Showtime(id, s.movieId(), s.auditoriumId(), s.startsAt(), s.endsAt(), s.basePrice(),
			ShowtimeStatus.CANCELLED, cancellation));
		bookingRows.values().removeIf(b -> b.showtimeId().equals(id) && b.status() == BookingStatus.PENDING);
		bookingRows.replaceAll((bookingId, b) -> b.showtimeId().equals(id) && b.status() == BookingStatus.CONFIRMED
			? new Booking(b.id(), b.customerId(), b.showtimeId(), BookingStatus.REFUNDED, b.createdAt(), b.expiresAt(),
				b.seats())
			: b);
		return true;
	}

	@Override
	public List<Showtime> findByStartsAtBetween(Instant fromInclusive, Instant toExclusive, UUID cinemaId,
		UUID movieId) {
		return rows.values().stream()
			.filter(s -> !s.startsAt().isBefore(fromInclusive) && s.startsAt().isBefore(toExclusive))
			.filter(s -> s.status() != ShowtimeStatus.CANCELLED)
			.filter(s -> cinemaId == null || auditoriums.findById(s.auditoriumId()).map(Auditorium::cinemaId)
				.filter(cinemaId::equals).isPresent())
			.filter(s -> movieId == null || s.movieId().equals(movieId))
			.sorted(Comparator.comparing(Showtime::startsAt).thenComparing(Showtime::id)).toList();
	}

	@Override
	public boolean existsOverlapping(UUID auditoriumId, Instant startsAt, Instant endsAt) {
		return rows.values().stream().anyMatch(s -> s.auditoriumId().equals(auditoriumId)
			&& s.startsAt().isBefore(endsAt) && s.endsAt().isAfter(startsAt) && s.status() != ShowtimeStatus.CANCELLED);
	}

	@Override
	public boolean existsByMovieId(UUID movieId) {
		return rows.values().stream().anyMatch(s -> s.movieId().equals(movieId));
	}

	@Override
	public boolean existsByAuditoriumId(UUID auditoriumId) {
		return rows.values().stream().anyMatch(s -> s.auditoriumId().equals(auditoriumId));
	}
}
