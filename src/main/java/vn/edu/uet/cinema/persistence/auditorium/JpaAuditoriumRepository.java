package vn.edu.uet.cinema.persistence.auditorium;

import static vn.edu.uet.cinema.persistence.ConstraintViolations.hasConstraint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.cinema.business.ConflictException;
import vn.edu.uet.cinema.business.auditorium.Auditorium;
import vn.edu.uet.cinema.business.auditorium.AuditoriumNameTakenException;
import vn.edu.uet.cinema.business.auditorium.AuditoriumRepository;

@Repository
class JpaAuditoriumRepository implements AuditoriumRepository {

	private final AuditoriumEntityRepository jpa;

	JpaAuditoriumRepository(AuditoriumEntityRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Auditorium> findByCinemaId(UUID cinemaId) {
		return jpa.findByCinemaId(cinemaId).stream().map(AuditoriumEntity::toAuditorium).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Auditorium> findById(UUID id) {
		return jpa.findById(id).map(AuditoriumEntity::toAuditorium);
	}

	@Override
	public boolean existsByCinemaId(UUID cinemaId) {
		return jpa.existsByCinemaId(cinemaId);
	}

	@Override
	public boolean existsByCinemaIdAndName(UUID cinemaId, String name) {
		return jpa.existsByCinemaIdAndName(cinemaId, name);
	}

	@Override
	@Transactional
	public Auditorium save(Auditorium auditorium) {
		try {
			return jpa.saveAndFlush(AuditoriumEntity.from(auditorium)).toAuditorium();
		} catch (DataIntegrityViolationException e) {
			if (hasConstraint(e, "uq_auditorium_cinema_name")) {
				throw new AuditoriumNameTakenException();
			}
			throw e;
		}
	}

	@Override
	@Transactional
	public void deleteById(UUID id) {
		try {
			jpa.deleteById(id);
			jpa.flush();
		} catch (DataIntegrityViolationException e) {
			if (hasConstraint(e, "fk_showtime_auditorium")) {
				throw new ConflictException("Auditorium has Showtimes");
			}
			throw e;
		}
	}
}
