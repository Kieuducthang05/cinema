package vn.edu.uet.cinema.api.movie;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.uet.cinema.business.movie.AgeRating;
import vn.edu.uet.cinema.business.movie.Movie;
import vn.edu.uet.cinema.business.movie.MovieService;
import vn.edu.uet.cinema.config.OpenApiConfig;

@RestController
@Tag(name = "Movies", description = "Movie catalogue management and public movie reads.")
class MovieController {

	private final MovieService movies;

	MovieController(MovieService movies) {
		this.movies = movies;
	}

	@PostMapping("/api/movies")
	@Operation(summary = "Create a movie", description = "Adds a movie to the catalogue. Duration must be positive; age rating "
		+ "is descriptive only. Requires ADMIN.")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	MovieResponse create(@Valid @RequestBody MovieRequest request) {
		return MovieResponse.from(movies.create(request.title(), request.description(), request.durationMinutes(),
			request.genre(), request.ageRating(), request.releaseDate(), request.posterUrl()));
	}

	@GetMapping("/api/movies")
	@Operation(summary = "List movies", description = "Returns all movies in the catalogue. This read is public; a supplied "
		+ "invalid bearer token is rejected.")
	List<MovieResponse> list() {
		return movies.list().stream().map(MovieResponse::from).toList();
	}

	@GetMapping("/api/movies/{id}")
	@Operation(summary = "Get a movie", description = "Returns one movie by UUID. This read is public; an unknown movie returns 404.")
	MovieResponse get(@Parameter(description = "Movie UUID.", example = "550e8400-e29b-41d4-a716-446655440002")
		@PathVariable UUID id) {
		return MovieResponse.from(movies.get(id));
	}

	@PutMapping("/api/movies/{id}")
	@Operation(summary = "Update a movie", description = "Replaces the movie's catalogue fields. A missing movie returns 404. "
		+ "Requires ADMIN.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	MovieResponse update(
		@Parameter(description = "Movie UUID.", example = "550e8400-e29b-41d4-a716-446655440002") @PathVariable UUID id,
		@Valid @RequestBody MovieRequest request) {
		return MovieResponse.from(movies.update(id, request.title(), request.description(), request.durationMinutes(),
			request.genre(), request.ageRating(), request.releaseDate(), request.posterUrl()));
	}

	@DeleteMapping("/api/movies/{id}")
	@Operation(summary = "Delete a movie", description = "Hard-deletes a movie only when it has no showtimes. Requires ADMIN; "
		+ "an unknown movie returns 404 and dependent showtimes cause 409.")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	void delete(@Parameter(description = "Movie UUID.", example = "550e8400-e29b-41d4-a716-446655440002")
		@PathVariable UUID id) {
		movies.delete(id);
	}

	@Schema(name = "MovieRequest")
	record MovieRequest(@NotBlank(message = "Title is required")
		@Size(max = 255, message = "Title is not allowed")
		@Schema(description = "Nonblank movie title.", minLength = 1, example = "The Example") String title,
		@NotBlank(message = "Description is required")
		@Schema(description = "Nonblank movie synopsis; no API length limit is defined.", minLength = 1,
			example = "A story about cinema.") String description,
		@NotNull(message = "Duration is required") @Positive(message = "Duration must be positive")
		@Schema(description = "Running time in whole minutes.", minimum = "1", example = "120") Integer durationMinutes,
		@NotBlank(message = "Genre is required") @Size(max = 255, message = "Genre is not allowed")
		@Schema(description = "Nonblank movie genre.", minLength = 1, example = "Drama") String genre,
		@NotNull(message = "Age rating is required")
		@Schema(description = "Display-only age rating.", example = "T13") AgeRating ageRating,
		@NotNull(message = "Release date is required")
		@Schema(description = "Release date as an ISO-8601 calendar date.", example = "2026-05-01") LocalDate releaseDate,
		@NotBlank(message = "Poster URL is required")
		@Schema(description = "Nonblank poster URL; no API length limit is defined.", minLength = 1,
			example = "https://example.com/poster.jpg") String posterUrl) {
	}

	record MovieResponse(
		@Schema(description = "Movie identifier.", example = "550e8400-e29b-41d4-a716-446655440000") UUID id,
		@Schema(description = "Movie title.", example = "The Example") String title,
		@Schema(description = "Movie synopsis.", example = "A story about cinema.") String description,
		@Schema(description = "Running time in whole minutes.", example = "120") int durationMinutes,
		@Schema(description = "Movie genre.", example = "Drama") String genre,
		@Schema(description = "Display-only age rating.", example = "T13") AgeRating ageRating,
		@Schema(description = "Release date.", example = "2026-05-01") LocalDate releaseDate,
		@Schema(description = "Poster URL.", example = "https://example.com/poster.jpg") String posterUrl) {

		static MovieResponse from(Movie movie) {
			return new MovieResponse(movie.id(), movie.title(), movie.description(), movie.durationMinutes(), movie.genre(),
				movie.ageRating(), movie.releaseDate(), movie.posterUrl());
		}
	}
}
