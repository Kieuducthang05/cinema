package vn.edu.uet.cinema.business.booking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Booking(UUID id, UUID customerId, UUID showtimeId, BookingStatus status, Instant createdAt,
	Instant expiresAt, List<BookedSeat> seats) {

	public Booking {
		seats = List.copyOf(seats);
	}

	/** The stored status, except that a Pending Booking whose Hold has lapsed at {@code now} is Expired. */
	public BookingStatus statusAt(Instant now) {
		return status == BookingStatus.PENDING && !now.isBefore(expiresAt) ? BookingStatus.EXPIRED : status;
	}

	public Booking confirmed() {
		return new Booking(id, customerId, showtimeId, BookingStatus.CONFIRMED, createdAt, expiresAt, seats);
	}

	public long totalPrice() {
		return seats.stream().mapToLong(BookedSeat::price).reduce(0L, Math::addExact);
	}
}
