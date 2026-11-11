package vn.edu.uet.cinema.business.booking;

import java.util.UUID;

/** A Seat in a Booking, with its price copied at booking time. */
public record BookedSeat(UUID seatId, long price) {
}
