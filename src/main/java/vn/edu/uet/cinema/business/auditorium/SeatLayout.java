package vn.edu.uet.cinema.business.auditorium;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SeatLayout {

	private static final int MAX_SEATS = 10_000;

	private SeatLayout() {
	}

	public static List<Seat> generate(int rows, int seatsPerRow, List<String> vipRows,
		List<String> coupleRows) {
		if (rows < 1 || rows > 26) {
			throw new InvalidSeatLayoutException("Rows must be between 1 and 26");
		}
		if (seatsPerRow < 1) {
			throw new InvalidSeatLayoutException("Seats per row must be positive");
		}
		if ((long) rows * seatsPerRow > MAX_SEATS) {
			throw new InvalidSeatLayoutException("Seat layout is not allowed");
		}
		validateRows(vipRows, rows, "VIP");
		validateRows(coupleRows, rows, "Couple");
		Set<String> vip = new HashSet<>(vipRows);
		Set<String> couple = new HashSet<>(coupleRows);
		if (!java.util.Collections.disjoint(vip, couple)) {
			throw new InvalidSeatLayoutException("VIP and couple rows must not overlap");
		}

		List<Seat> seats = new ArrayList<>(rows * seatsPerRow);
		for (int rowNumber = 0; rowNumber < rows; rowNumber++) {
			String row = String.valueOf((char) ('A' + rowNumber));
			SeatType type = vip.contains(row) ? SeatType.VIP
				: couple.contains(row) ? SeatType.COUPLE : SeatType.STANDARD;
			for (int number = 1; number <= seatsPerRow; number++) {
				seats.add(new Seat(UUID.randomUUID(), row, number, type));
			}
		}
		return List.copyOf(seats);
	}

	private static void validateRows(List<String> configuredRows, int rows, String type) {
		if (configuredRows == null) {
			throw new InvalidSeatLayoutException(type + " rows are required");
		}
		Set<String> seen = new HashSet<>();
		for (String row : configuredRows) {
			if (row == null || row.length() != 1 || row.charAt(0) < 'A'
				|| row.charAt(0) >= 'A' + rows || !seen.add(row)) {
				throw new InvalidSeatLayoutException("Invalid " + type + " row");
			}
		}
	}
}
