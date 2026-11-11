package vn.edu.uet.cinema.business.booking;

import java.util.List;
import java.util.UUID;
import vn.edu.uet.cinema.business.ConflictException;

/** Some of the requested Seats are HELD or SOLD. */
public class SeatsTakenException extends ConflictException {

	private final List<UUID> seatIds;

	public SeatsTakenException(List<UUID> seatIds) {
		super("Seat already taken: " + seatIds);
		this.seatIds = List.copyOf(seatIds);
	}

	public List<UUID> seatIds() {
		return seatIds;
	}
}
