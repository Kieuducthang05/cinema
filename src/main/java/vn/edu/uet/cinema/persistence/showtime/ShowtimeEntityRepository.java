package vn.edu.uet.cinema.persistence.showtime;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ShowtimeEntityRepository extends JpaRepository<ShowtimeEntity, UUID> {

	@Query("""
		select s from ShowtimeEntity s where s.startsAt >= :from and s.startsAt < :to
		and s.status <> vn.edu.uet.cinema.business.showtime.ShowtimeStatus.CANCELLED
		and (:cinemaId is null or s.auditoriumId in (select a.id from AuditoriumEntity a where a.cinemaId = :cinemaId))
		and (:movieId is null or s.movieId = :movieId) order by s.startsAt, s.id
		""")
	List<ShowtimeEntity> findStartingBetween(@Param("from") Instant fromInclusive, @Param("to") Instant toExclusive,
		@Param("cinemaId") UUID cinemaId, @Param("movieId") UUID movieId);

	@Query("""
		select case when count(s) > 0 then true else false end from ShowtimeEntity s
		where s.auditoriumId = :auditoriumId and s.startsAt < :endsAt and s.endsAt > :startsAt
		and s.status <> vn.edu.uet.cinema.business.showtime.ShowtimeStatus.CANCELLED
		""")
	boolean existsOverlapping(UUID auditoriumId, Instant startsAt, Instant endsAt);

	/** ADR 0004: lapsed Holds never block deleting their Showtime. booked_seat rows go by ON DELETE CASCADE. */
	@Modifying
	@Query(nativeQuery = true, value = """
		delete from booking where showtime_id = :showtimeId and status = 'PENDING' and expires_at <= :now
		""")
	void deleteLapsedHolds(UUID showtimeId, Instant now);

	@Modifying
	@Query(nativeQuery = true, value = """
		update showtime set status = 'CANCELLED', cancellation_reason = :reason, cancellation_note = :note
		where id = :id and status = 'SCHEDULED' and ends_at > :now
		""")
	int cancel(UUID id, String reason, String note, Instant now);

	/** Expired ones too: they are stored as PENDING. booked_seat rows go by ON DELETE CASCADE. */
	@Modifying
	@Query(nativeQuery = true, value = "delete from booking where showtime_id = :showtimeId and status = 'PENDING'")
	void deletePendingBookings(UUID showtimeId);

	@Modifying
	@Query(nativeQuery = true, value = """
		update booking set status = 'REFUNDED' where showtime_id = :showtimeId and status = 'CONFIRMED'
		""")
	void refundConfirmedBookings(UUID showtimeId);

	boolean existsByMovieId(UUID movieId);

	boolean existsByAuditoriumId(UUID auditoriumId);
}
