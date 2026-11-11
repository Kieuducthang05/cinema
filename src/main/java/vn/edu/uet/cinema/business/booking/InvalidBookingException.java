package vn.edu.uet.cinema.business.booking;

public class InvalidBookingException extends RuntimeException {

	public InvalidBookingException(String message) {
		super(message);
	}
}
