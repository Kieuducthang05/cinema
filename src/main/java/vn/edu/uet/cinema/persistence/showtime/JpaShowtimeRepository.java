package vn.edu.uet.cinema.persistence.showtime;

import static vn.edu.uet.cinema.persistence.ConstraintViolations.hasConstraint;
import static vn.edu.uet.cinema.persistence.ConstraintViolations.hasSqlState;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.cinema.business.ConflictException;
import vn.edu.uet.cinema.business.showtime.Cancellation;
import vn.edu.uet.cinema.business.showtime.Showtime;
import vn.edu.uet.cinema.business.showtime.ShowtimeRepository;

@Repository
class JpaShowtimeRepository implements ShowtimeRepository {

	private final ShowtimeEntityRepository jpa;

	JpaShowtimeRepository(ShowtimeEntityRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	@Transactional
	public Showtime save(Showtime showtime) {
		try {
			return jpa.saveAndFlush(ShowtimeEntity.from(showtime)).toShowtime();
		} catch (DataIntegrityViolationException e) {
			if (hasConstraint(e, "fk_showtime_movie")) {
				throw new ConflictException("Movie no longer exists");
			}
			if (hasConstraint(e, "fk_showtime_auditorium")) {
				throw new ConflictException("Auditorium no longer exists");
			}
			if (hasSqlState(e, "23P01")) {
				throw new ConflictException("Auditorium has an overlapping Showtime");
			}
			throw e;
		}
	}

	@Override
	public Optional<Showtime> findById(UUID id) {
		return jpa.findById(id).map(ShowtimeEntity::toShowtime);
	}

	@Override
	@Transactional
	public void deleteById(UUID id, Instant now) {
		try {
			jpa.deleteLapsedHolds(id, now);
			jpa.deleteById(id);
			jpa.flush();
		} catch (DataIntegrityViolationException e) {
			if (hasConstraint(e, "fk_booking_showtime")) {
				throw new ConflictException("Showtime has Bookings");
			}
			throw e;
		}
	}

	@Override
	@Transactional
	public boolean cancel(UUID id, Cancellation cancellation, Instant now) {
		if (jpa.cancel(id, cancellation.reason().name(), cancellation.note(), now) == 0) {
			return false;
		}
		// Delete before refunding: a payment that commits in between is then refunded, never left Confirmed.
		jpa.deletePendingBookings(id);
		jpa.refundConfirmedBookings(id);
		return true;
	}

	@Override
	public List<Showtime> findByStartsAtBetween(Instant fromInclusive, Instant toExclusive, UUID cinemaId,
		UUID movieId) {
		return jpa.findStartingBetween(fromInclusive, toExclusive, cinemaId, movieId).stream()
			.map(ShowtimeEntity::toShowtime).toList();
	}

	@Override
	public boolean existsOverlapping(UUID auditoriumId, Instant startsAt, Instant endsAt) {
		return jpa.existsOverlapping(auditoriumId, startsAt, endsAt);
	}

	@Override
	public boolean existsByMovieId(UUID movieId) {
		return jpa.existsByMovieId(movieId);
	}

	@Override
	public boolean existsByAuditoriumId(UUID auditoriumId) {
		return jpa.existsByAuditoriumId(auditoriumId);
	}
}
