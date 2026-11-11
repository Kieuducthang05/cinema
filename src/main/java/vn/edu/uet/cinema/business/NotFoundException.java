package vn.edu.uet.cinema.business;

/** The requested thing does not exist, or the caller may not know it exists. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
