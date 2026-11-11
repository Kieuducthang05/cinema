package vn.edu.uet.cinema.business.movie;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MovieRepository {

	List<Movie> findAll();

	Optional<Movie> findById(UUID id);

	Movie save(Movie movie);

	void deleteById(UUID id);
}
