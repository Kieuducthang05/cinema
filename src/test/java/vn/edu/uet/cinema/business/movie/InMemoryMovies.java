package vn.edu.uet.cinema.business.movie;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory {@link MovieRepository} for business tests. */
public class InMemoryMovies implements MovieRepository {

	private final Map<UUID, Movie> rows = new LinkedHashMap<>();

	@Override
	public List<Movie> findAll() {
		return new ArrayList<>(rows.values());
	}

	@Override
	public Optional<Movie> findById(UUID id) {
		return Optional.ofNullable(rows.get(id));
	}

	@Override
	public Movie save(Movie movie) {
		rows.put(movie.id(), movie);
		return movie;
	}

	@Override
	public void deleteById(UUID id) {
		rows.remove(id);
	}
}
