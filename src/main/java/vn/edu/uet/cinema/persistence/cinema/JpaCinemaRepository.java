package vn.edu.uet.cinema.persistence.cinema;

import static vn.edu.uet.cinema.persistence.ConstraintViolations.hasConstraint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.cinema.business.ConflictException;
import vn.edu.uet.cinema.business.cinema.Cinema;
import vn.edu.uet.cinema.business.cinema.CinemaRepository;

@Repository
class JpaCinemaRepository implements CinemaRepository {

	private final CinemaEntityRepository jpa;

	JpaCinemaRepository(CinemaEntityRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public List<Cinema> findAll() {
		return jpa.findAll().stream().map(CinemaEntity::toCinema).toList();
	}

	@Override
	public Optional<Cinema> findById(UUID id) {
		return jpa.findById(id).map(CinemaEntity::toCinema);
	}

	@Override
	public Cinema save(Cinema cinema) {
		return jpa.save(CinemaEntity.from(cinema)).toCinema();
	}

	@Override
	@Transactional
	public void deleteById(UUID id) {
		try {
			jpa.deleteById(id);
			jpa.flush();
		} catch (DataIntegrityViolationException e) {
			if (hasConstraint(e, "fk_auditorium_cinema")) {
				throw new ConflictException("Cinema has Auditoriums");
			}
			throw e;
		}
	}
}
