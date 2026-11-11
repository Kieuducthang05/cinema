package vn.edu.uet.cinema.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ConstraintViolationsTest {

	@Test
	void findsNamedConstraintAnywhereInTheChain() {
		var error = new DataIntegrityViolationException("wrapped",
			new ConstraintViolationException("violation", new SQLException("x", "23503"), "fk_a"));

		assertThat(ConstraintViolations.hasConstraint(error, "fk_a")).isTrue();
		assertThat(ConstraintViolations.hasConstraint(error, "fk_b")).isFalse();
	}

	@Test
	void findsSqlStateAnywhereInTheChain() {
		var error = new DataIntegrityViolationException("wrapped", new RuntimeException(new SQLException("x", "23P01")));

		assertThat(ConstraintViolations.hasSqlState(error, "23P01")).isTrue();
		assertThat(ConstraintViolations.hasSqlState(error, "23505")).isFalse();
	}
}
