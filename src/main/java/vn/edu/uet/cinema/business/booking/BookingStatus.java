package vn.edu.uet.cinema.business.booking;

/**
 * PENDING, CONFIRMED and REFUNDED are stored. EXPIRED is a PENDING Booking past its {@code expiresAt}, never stored.
 * REFUNDED is a CONFIRMED Booking whose Showtime was Cancelled.
 */
public enum BookingStatus {
	PENDING, CONFIRMED, EXPIRED, REFUNDED
}
