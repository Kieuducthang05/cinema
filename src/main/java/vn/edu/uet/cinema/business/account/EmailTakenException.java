package vn.edu.uet.cinema.business.account;

import vn.edu.uet.cinema.business.ConflictException;

public class EmailTakenException extends ConflictException {

	public EmailTakenException() {
		super("Email already registered");
	}
}
