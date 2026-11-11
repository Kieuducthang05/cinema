package vn.edu.uet.cinema.persistence.movie;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import vn.edu.uet.cinema.business.movie.AgeRating;
import vn.edu.uet.cinema.business.movie.Movie;

@Entity
@Table(name = "movie")
class MovieEntity {

	@Id
	private UUID id;
	private String title;
	@Column(columnDefinition = "text")
	private String description;
	@Column(name = "duration_minutes")
	private int durationMinutes;
	private String genre;
	@Enumerated(EnumType.STRING)
	@Column(name = "age_rating", length = 4)
	private AgeRating ageRating;
	@Column(name = "release_date")
	private LocalDate releaseDate;
	@Column(name = "poster_url", columnDefinition = "text")
	private String posterUrl;

	protected MovieEntity() {
	}

	static MovieEntity from(Movie movie) {
		MovieEntity entity = new MovieEntity();
		entity.id = movie.id();
		entity.title = movie.title();
		entity.description = movie.description();
		entity.durationMinutes = movie.durationMinutes();
		entity.genre = movie.genre();
		entity.ageRating = movie.ageRating();
		entity.releaseDate = movie.releaseDate();
		entity.posterUrl = movie.posterUrl();
		return entity;
	}

	Movie toMovie() {
		return new Movie(id, title, description, durationMinutes, genre, ageRating, releaseDate, posterUrl);
	}
}
