package vn.edu.uet.cinema.business.auditorium;

import vn.edu.uet.cinema.business.ConflictException;

public class AuditoriumNameTakenException extends ConflictException {

	public AuditoriumNameTakenException() {
		super("Auditorium name is already used in this Cinema");
	}
}
