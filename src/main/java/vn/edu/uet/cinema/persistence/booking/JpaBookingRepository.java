package vn.edu.uet.cinema.persistence.booking;

import static vn.edu.uet.cinema.persistence.ConstraintViolations.hasConstraint;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import vn.edu.uet.cinema.business.ConflictException;
import vn.edu.uet.cinema.business.booking.BookedSeat;
import vn.edu.uet.cinema.business.booking.Booking;
import vn.edu.uet.cinema.business.booking.BookingRepository;
import vn.edu.uet.cinema.business.booking.SeatsTakenException;

@Repository
class JpaBookingRepository implements BookingRepository {

	private final BookingEntityRepository jpa;
	private final TransactionOperations transaction;

	JpaBookingRepository(BookingEntityRepository jpa, TransactionOperations transaction) {
		this.jpa = jpa;
		this.transaction = transaction;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Booking> findById(UUID id) {
		return jpa.findById(id).map(BookingEntity::toBooking);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Booking> findByCustomer(UUID customerId) {
		return jpa.findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(BookingEntity::toBooking).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Booking> findLivePending(UUID customerId, Instant now) {
		return jpa.findLivePending(customerId, now).map(BookingEntity::toBooking);
	}

	/** Not {@code @Transactional}: each attempt runs in its own transaction, so a deadlock loser can retry. */
	@Override
	public Booking saveNew(Booking booking, Instant now) {
		try {
			return insert(booking, now);
		} catch (PessimisticLockingFailureException deadlock) {
			// Overlapping lapsed-Hold deletes can deadlock even when the two Bookings want different Seats.
			try {
				return insert(booking, now);
			} catch (PessimisticLockingFailureException again) {
				throw new ConflictException("Seats are being booked by others right now, try again");
			}
		}
	}

	private Booking insert(Booking booking, Instant now) {
		List<UUID> seatIds = booking.seats().stream().map(BookedSeat::seatId).toList();
		try {
			return transaction.execute(status -> {
				if ("CANCELLED".equals(jpa.lockShowtimeStatus(booking.showtimeId()))) {
					throw new ConflictException("Showtime is Cancelled");
				}
				jpa.deleteLapsedHolds(booking.customerId(), booking.showtimeId(), seatIds, now);
				return jpa.saveAndFlush(BookingEntity.from(booking)).toBooking();
			});
		} catch (DataIntegrityViolationException e) {
			// The insert that loses the race fails here (ADR 0004).
			if (hasConstraint(e, "uq_booked_seat_showtime_seat")) {
				// Rolled back by now, so the winner's Seats are visible to a fresh read. If the winner was abandoned
				// in between, the list is empty: the Seats are free again and the client can simply retry.
				Set<UUID> occupied = jpa.findOccupiedSeats(booking.showtimeId(), now).stream()
					.map(BookingEntityRepository.OccupiedSeat::getSeatId).collect(Collectors.toSet());
				throw new SeatsTakenException(seatIds.stream().filter(occupied::contains).toList());
			}
			if (hasConstraint(e, "uq_booking_customer_pending")) {
				throw new ConflictException("Customer already has a Pending Booking");
			}
			if (hasConstraint(e, "fk_booking_showtime")) {
				throw new ConflictException("Showtime no longer exists");
			}
			throw e;
		}
	}

	@Override
	@Transactional
	public boolean confirm(UUID id, Instant now) {
		return jpa.confirm(id, now) == 1;
	}

	@Override
	@Transactional
	public boolean deletePending(UUID id) {
		return jpa.deletePending(id) == 1;
	}
}
