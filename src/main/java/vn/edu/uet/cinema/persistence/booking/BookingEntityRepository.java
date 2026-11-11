package vn.edu.uet.cinema.persistence.booking;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface BookingEntityRepository extends JpaRepository<BookingEntity, UUID> {

	@Query("""
		select b from BookingEntity b where b.customerId = :customerId
		and b.status = vn.edu.uet.cinema.business.booking.BookingStatus.PENDING and b.expiresAt > :now
		""")
	Optional<BookingEntity> findLivePending(UUID customerId, Instant now);

	List<BookingEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

	/** The conditional update of ADR 0004: only a live Hold can be paid. */
	@Modifying
	@Query(nativeQuery = true, value = """
		update booking set status = 'CONFIRMED' where id = :id and status = 'PENDING' and expires_at > :now
		""")
	int confirm(UUID id, Instant now);

	/** booked_seat rows go by ON DELETE CASCADE. */
	@Modifying
	@Query(nativeQuery = true, value = "delete from booking where id = :id and status = 'PENDING'")
	int deletePending(UUID id);

	/**
	 * Holds the Showtime row until commit, so a cancel waits for this Booking and then deletes it, or has already
	 * committed and is seen here. Null if the Showtime does not exist.
	 */
	@Query(nativeQuery = true, value = "select status from showtime where id = :showtimeId for share")
	String lockShowtimeStatus(UUID showtimeId);

	/** Lapsed Holds on these Seats, and the Customer's own lapsed one. booked_seat rows go by ON DELETE CASCADE. */
	@Modifying
	@Query(nativeQuery = true, value = """
		delete from booking b where b.status = 'PENDING' and b.expires_at <= :now
		and (b.customer_id = :customerId or b.id in (select s.booking_id from booked_seat s
			where s.showtime_id = :showtimeId and s.seat_id in (:seatIds)))
		""")
	void deleteLapsedHolds(UUID customerId, UUID showtimeId, Collection<UUID> seatIds, Instant now);

	@Query(nativeQuery = true, value = """
		select s.seat_id as seatId, b.status as status from booking b join booked_seat s on s.booking_id = b.id
		where b.showtime_id = :showtimeId and (b.status = 'CONFIRMED' or b.expires_at > :now)
		""")
	List<OccupiedSeat> findOccupiedSeats(UUID showtimeId, Instant now);

	interface OccupiedSeat {

		UUID getSeatId();

		String getStatus();
	}
}
