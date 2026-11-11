package vn.edu.uet.cinema.business.auditorium;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory {@link AuditoriumRepository} for business tests. */
public class InMemoryAuditoriums implements AuditoriumRepository {

	private final Map<UUID, Auditorium> rows = new LinkedHashMap<>();

	@Override
	public List<Auditorium> findByCinemaId(UUID cinemaId) {
		return rows.values().stream().filter(a -> a.cinemaId().equals(cinemaId)).toList();
	}

	@Override
	public Optional<Auditorium> findById(UUID id) {
		return Optional.ofNullable(rows.get(id));
	}

	@Override
	public boolean existsByCinemaId(UUID cinemaId) {
		return !findByCinemaId(cinemaId).isEmpty();
	}

	@Override
	public boolean existsByCinemaIdAndName(UUID cinemaId, String name) {
		return findByCinemaId(cinemaId).stream().anyMatch(a -> a.name().equals(name));
	}

	@Override
	public Auditorium save(Auditorium auditorium) {
		rows.put(auditorium.id(), auditorium);
		return auditorium;
	}

	@Override
	public void deleteById(UUID id) {
		rows.remove(id);
	}
}
