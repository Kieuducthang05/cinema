package vn.edu.uet.cinema.business.movie;

import java.time.LocalDate;
import java.util.UUID;

public record Movie(UUID id, String title, String description, int durationMinutes, String genre,
	AgeRating ageRating, LocalDate releaseDate, String posterUrl) {
}
