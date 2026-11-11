package vn.edu.uet.cinema.persistence.booking;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import vn.edu.uet.cinema.business.booking.BookingStatus;
import vn.edu.uet.cinema.business.showtime.SeatOccupancyReader;
import vn.edu.uet.cinema.business.showtime.SeatStatus;

@Component
class JpaSeatOccupancyReader implements SeatOccupancyReader {

	private final BookingEntityRepository jpa;

	JpaSeatOccupancyReader(BookingEntityRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Map<UUID, SeatStatus> statuses(UUID showtimeId, Instant now) {
		return jpa.findOccupiedSeats(showtimeId, now).stream().collect(Collectors.toMap(
			BookingEntityRepository.OccupiedSeat::getSeatId,
			seat -> BookingStatus.valueOf(seat.getStatus()) == BookingStatus.CONFIRMED ? SeatStatus.SOLD
				: SeatStatus.HELD));
	}
}
