package vn.edu.uet.cinema.business.account;

public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Invalid email or password");
	}
}
