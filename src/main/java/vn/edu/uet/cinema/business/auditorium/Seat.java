package vn.edu.uet.cinema.business.auditorium;

import java.util.UUID;

public record Seat(UUID id, String row, int number, SeatType type) {

	public String label() {
		return row + number;
	}
}
