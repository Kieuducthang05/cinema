package vn.edu.uet.cinema.business.cinema;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CinemaRepository {

	List<Cinema> findAll();

	Optional<Cinema> findById(UUID id);

	Cinema save(Cinema cinema);

	void deleteById(UUID id);
}
