package vn.edu.uet.cinema.business.booking;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import vn.edu.uet.cinema.business.showtime.SeatOccupancyReader;
import vn.edu.uet.cinema.business.showtime.SeatStatus;

/** In-memory {@link SeatOccupancyReader} reading the Booking adapter's rows. */
public class InMemorySeatOccupancy implements SeatOccupancyReader {

	private final InMemoryBookings bookings;

	public InMemorySeatOccupancy(InMemoryBookings bookings) {
		this.bookings = bookings;
	}

	@Override
	public Map<UUID, SeatStatus> statuses(UUID showtimeId, Instant now) {
		Map<UUID, SeatStatus> statuses = new HashMap<>();
		for (Booking b : bookings.rows.values()) {
			if (!b.showtimeId().equals(showtimeId)) {
				continue;
			}
			SeatStatus status = b.status() == BookingStatus.CONFIRMED ? SeatStatus.SOLD
				: b.expiresAt().isAfter(now) ? SeatStatus.HELD : null;
			if (status != null) {
				b.seats().forEach(seat -> statuses.put(seat.seatId(), status));
			}
		}
		return statuses;
	}
}
