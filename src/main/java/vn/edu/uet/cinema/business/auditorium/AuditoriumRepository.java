package vn.edu.uet.cinema.business.auditorium;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditoriumRepository {

	List<Auditorium> findByCinemaId(UUID cinemaId);

	Optional<Auditorium> findById(UUID id);

	boolean existsByCinemaId(UUID cinemaId);

	boolean existsByCinemaIdAndName(UUID cinemaId, String name);

	Auditorium save(Auditorium auditorium);

	void deleteById(UUID id);
}
