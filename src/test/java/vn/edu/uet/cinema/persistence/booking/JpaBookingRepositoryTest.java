package vn.edu.uet.cinema.persistence.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.support.TransactionOperations;
import vn.edu.uet.cinema.business.ConflictException;
import vn.edu.uet.cinema.business.booking.BookedSeat;
import vn.edu.uet.cinema.business.booking.Booking;
import vn.edu.uet.cinema.business.booking.BookingStatus;
import vn.edu.uet.cinema.business.booking.SeatsTakenException;

/**
 * A real deadlock between lapsed-Hold deletes depends on the order PostgreSQL visits rows, so it can't be forced
 * reliably against the database; the retry is checked here with the deadlock thrown by a mock.
 */
class JpaBookingRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

	private final BookingEntityRepository jpa = mock(BookingEntityRepository.class);
	private final JpaBookingRepository repository = new JpaBookingRepository(jpa,
		TransactionOperations.withoutTransaction());
	private final Booking booking = new Booking(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
		BookingStatus.PENDING, NOW, NOW.plusSeconds(600), List.of(new BookedSeat(UUID.randomUUID(), 100_000)));

	@Test
	void bookingThatLosesADeadlockIsRetriedOnce() {
		when(jpa.saveAndFlush(any())).thenThrow(new CannotAcquireLockException("deadlock detected"))
			.thenAnswer(invocation -> invocation.getArgument(0));

		assertThat(repository.saveNew(booking, NOW)).isEqualTo(booking);
		verify(jpa, times(2)).saveAndFlush(any());
	}

	@Test
	void secondDeadlockIsAConflictThatDoesNotClaimTheSeatsAreTaken() {
		when(jpa.saveAndFlush(any())).thenThrow(new CannotAcquireLockException("deadlock detected"));

		assertThatThrownBy(() -> repository.saveNew(booking, NOW)).isInstanceOf(ConflictException.class)
			.isNotInstanceOf(SeatsTakenException.class);
		verify(jpa, times(2)).saveAndFlush(any());
	}
}
