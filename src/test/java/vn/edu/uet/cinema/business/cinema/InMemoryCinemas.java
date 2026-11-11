package vn.edu.uet.cinema.business.cinema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory {@link CinemaRepository} for business tests. */
public class InMemoryCinemas implements CinemaRepository {

	private final Map<UUID, Cinema> rows = new LinkedHashMap<>();

	@Override
	public List<Cinema> findAll() {
		return new ArrayList<>(rows.values());
	}

	@Override
	public Optional<Cinema> findById(UUID id) {
		return Optional.ofNullable(rows.get(id));
	}

	@Override
	public Cinema save(Cinema cinema) {
		rows.put(cinema.id(), cinema);
		return cinema;
	}

	@Override
	public void deleteById(UUID id) {
		rows.remove(id);
	}
}
