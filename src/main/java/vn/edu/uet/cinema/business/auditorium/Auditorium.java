package vn.edu.uet.cinema.business.auditorium;

import java.util.List;
import java.util.UUID;

public record Auditorium(UUID id, UUID cinemaId, String name, int rows, int seatsPerRow, List<Seat> seats) {

	public Auditorium {
		seats = List.copyOf(seats);
	}
}
