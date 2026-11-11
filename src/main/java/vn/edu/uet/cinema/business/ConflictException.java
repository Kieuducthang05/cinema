package vn.edu.uet.cinema.business;

/** The request is well-formed but breaks a business rule given the current state. */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}
}
