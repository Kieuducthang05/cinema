package vn.edu.uet.cinema.persistence;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;

/** Recognises database constraint violations anywhere in an exception's cause chain. */
public final class ConstraintViolations {

	private ConstraintViolations() {
	}

	public static boolean hasConstraint(Throwable error, String constraintName) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation
				&& constraintName.equals(violation.getConstraintName())) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasSqlState(Throwable error, String sqlState) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && sqlState.equals(sql.getSQLState())) {
				return true;
			}
		}
		return false;
	}
}
