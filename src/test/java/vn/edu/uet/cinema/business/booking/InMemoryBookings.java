package vn.edu.uet.cinema.business.booking;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import vn.edu.uet.cinema.business.ConflictException;
import vn.edu.uet.cinema.business.showtime.InMemoryShowtimes;
import vn.edu.uet.cinema.business.showtime.ShowtimeStatus;

/**
 * In-memory {@link BookingRepository} for business tests, on the Showtime adapter's Booking rows. Single-threaded
 * behaviour only: deadlocks, retries and locks stay covered against PostgreSQL.
 */
public class InMemoryBookings implements BookingRepository {

	private final InMemoryShowtimes showtimes;
	final Map<UUID, Booking> rows;

	public InMemoryBookings(InMemoryShowtimes showtimes) {
		this.showtimes = showtimes;
		this.rows = showtimes.bookingRows;
	}

	@Override
	public Optional<Booking> findById(UUID id) {
		return Optional.ofNullable(rows.get(id));
	}

	@Override
	public List<Booking> findByCustomer(UUID customerId) {
		return rows.values().stream().filter(b -> b.customerId().equals(customerId))
			.sorted(Comparator.comparing(Booking::createdAt).reversed()).toList();
	}

	@Override
	public Optional<Booking> findLivePending(UUID customerId, Instant now) {
		return rows.values().stream()
			.filter(b -> b.customerId().equals(customerId) && b.statusAt(now) == BookingStatus.PENDING).findFirst();
	}

	@Override
	public Booking saveNew(Booking booking, Instant now) {
		showtimes.findById(booking.showtimeId()).filter(s -> s.status() == ShowtimeStatus.CANCELLED)
			.ifPresent(s -> {
				throw new ConflictException("Showtime is Cancelled");
			});
		List<UUID> seatIds = booking.seats().stream().map(BookedSeat::seatId).toList();
		rows.values().removeIf(b -> b.statusAt(now) == BookingStatus.EXPIRED && (b.customerId().equals(booking.customerId())
			|| b.showtimeId().equals(booking.showtimeId()) && b.seats().stream().anyMatch(s -> seatIds.contains(s.seatId()))));
		Set<UUID> taken = rows.values().stream().filter(b -> b.showtimeId().equals(booking.showtimeId()))
			.flatMap(b -> b.seats().stream()).map(BookedSeat::seatId).collect(Collectors.toSet());
		List<UUID> clash = seatIds.stream().filter(taken::contains).toList();
		if (!clash.isEmpty()) {
			throw new SeatsTakenException(clash);
		}
		if (rows.values().stream().anyMatch(b -> b.customerId().equals(booking.customerId())
			&& b.status() == BookingStatus.PENDING)) {
			throw new ConflictException("Customer already has a Pending Booking");
		}
		rows.put(booking.id(), booking);
		return booking;
	}

	@Override
	public boolean confirm(UUID id, Instant now) {
		Booking b = rows.get(id);
		if (b == null || b.statusAt(now) != BookingStatus.PENDING) {
			return false;
		}
		rows.put(id, b.confirmed());
		return true;
	}

	@Override
	public boolean deletePending(UUID id) {
		return rows.values().removeIf(b -> b.id().equals(id) && b.status() == BookingStatus.PENDING);
	}
}
