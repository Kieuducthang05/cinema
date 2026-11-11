package vn.edu.uet.cinema.persistence.showtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import vn.edu.uet.cinema.TestcontainersConfiguration;
import vn.edu.uet.cinema.business.ConflictException;
import vn.edu.uet.cinema.business.auditorium.Auditorium;
import vn.edu.uet.cinema.business.auditorium.AuditoriumService;
import vn.edu.uet.cinema.business.cinema.CinemaService;
import vn.edu.uet.cinema.business.movie.AgeRating;
import vn.edu.uet.cinema.business.movie.MovieService;
import vn.edu.uet.cinema.business.showtime.Cancellation;
import vn.edu.uet.cinema.business.showtime.CancellationReason;
import vn.edu.uet.cinema.business.showtime.Showtime;
import vn.edu.uet.cinema.business.showtime.ShowtimeService;
import vn.edu.uet.cinema.business.showtime.ShowtimeStatus;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShowtimePersistenceTest {

	@Autowired ShowtimeService showtimes;
	@Autowired vn.edu.uet.cinema.business.showtime.ShowtimeRepository repository;
	@Autowired MovieService movies;
	@Autowired CinemaService cinemas;
	@Autowired AuditoriumService auditoriums;
	@Autowired vn.edu.uet.cinema.business.movie.MovieRepository movieRepository;
	@Autowired vn.edu.uet.cinema.business.auditorium.AuditoriumRepository auditoriumRepository;

	@Test
	void rejectsOverlapButAllowsBackToBackAndKeepsOriginalEndAfterMovieEdit() {
		UUID movieId = movies.create("Film", "Synopsis", 90, "Drama", AgeRating.P,
			LocalDate.parse("2026-09-01"), "poster").id();
		UUID cinemaId = cinemas.create("Cinema " + UUID.randomUUID(), "Address").id();
		Auditorium hall = auditoriums.create(cinemaId, "Hall", 1, 1, List.of(), List.of());
		Instant start = Instant.parse("2026-10-01T12:00:00Z");
		Showtime first = showtimes.create(movieId, hall.id(), start, 100_000);
		assertThatThrownBy(() -> showtimes.create(movieId, hall.id(), start.plusSeconds(30), 100_000))
			.isInstanceOf(ConflictException.class);
		Showtime adjacent = showtimes.create(movieId, hall.id(), first.endsAt(), 100_000);
		movies.update(movieId, "Film", "Synopsis", 180, "Drama", AgeRating.P,
			LocalDate.parse("2026-09-01"), "poster");
		assertThat(showtimes.get(first.id()).endsAt()).isEqualTo(start.plusSeconds(105 * 60));
		assertThat(showtimes.get(adjacent.id()).startsAt()).isEqualTo(first.endsAt());
	}

	@Test
	void persistenceRejectsOverlapEvenWhenServicePrecheckWasBypassed() {
		UUID movieId = movies.create("Film", "Synopsis", 90, "Drama", AgeRating.P,
			LocalDate.parse("2026-09-01"), "poster").id();
		UUID cinemaId = cinemas.create("Cinema " + UUID.randomUUID(), "Address").id();
		UUID hallId = auditoriums.create(cinemaId, "Hall", 1, 1, List.of(), List.of()).id();
		Instant start = Instant.parse("2026-10-01T12:00:00Z");
		showtimes.create(movieId, hallId, start, 100_000);

		assertThatThrownBy(() -> repository.save(new Showtime(UUID.randomUUID(), movieId, hallId,
			start.plusSeconds(60), start.plusSeconds(3_600), 100_000)))
			.isInstanceOf(ConflictException.class);
	}

	@Test
	void persistenceRejectsSaveWhenMovieOrAuditoriumWasDeletedAfterLookup() {
		UUID movieId = movies.create("Film", "Synopsis", 90, "Drama", AgeRating.P,
			LocalDate.parse("2026-09-01"), "poster").id();
		UUID cinemaId = cinemas.create("Cinema " + UUID.randomUUID(), "Address").id();
		UUID hallId = auditoriums.create(cinemaId, "Hall", 1, 1, List.of(), List.of()).id();
		Instant start = Instant.parse("2026-10-01T12:00:00Z");

		assertThatThrownBy(() -> repository.save(new Showtime(UUID.randomUUID(), UUID.randomUUID(), hallId,
			start, start.plusSeconds(3_600), 100_000)))
			.isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> repository.save(new Showtime(UUID.randomUUID(), movieId, UUID.randomUUID(),
			start, start.plusSeconds(3_600), 100_000)))
			.isInstanceOf(ConflictException.class);
	}

	@Test
	void refusesToDeleteMovieOrAuditoriumWithShowtimesThenAllowsAfterShowtimeDelete() {
		UUID movieId = movies.create("Film", "Synopsis", 90, "Drama", AgeRating.P,
			LocalDate.parse("2026-09-01"), "poster").id();
		UUID cinemaId = cinemas.create("Cinema " + UUID.randomUUID(), "Address").id();
		UUID auditoriumId = auditoriums.create(cinemaId, "Hall", 1, 1, List.of(), List.of()).id();
		Showtime showtime = showtimes.create(movieId, auditoriumId, Instant.parse("2026-10-01T12:00:00Z"), 100_000);

		assertThatThrownBy(() -> movies.delete(movieId)).isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> auditoriums.delete(auditoriumId)).isInstanceOf(ConflictException.class);
		assertThat(showtimes.get(showtime.id())).isEqualTo(showtime);
		showtimes.delete(showtime.id(), Instant.now());
		movies.delete(movieId);
		auditoriums.delete(auditoriumId);
	}

	@Test
	void adapterDeleteTranslatesForeignKeyViolationToConflictWhenServiceGuardWasBypassed() {
		UUID movieId = movies.create("Film", "Synopsis", 90, "Drama", AgeRating.P,
			LocalDate.parse("2026-09-01"), "poster").id();
		UUID cinemaId = cinemas.create("Cinema " + UUID.randomUUID(), "Address").id();
		UUID auditoriumId = auditoriums.create(cinemaId, "Hall", 1, 1, List.of(), List.of()).id();
		Instant start = Instant.parse("2026-10-01T12:00:00Z");
		repository.save(new Showtime(UUID.randomUUID(), movieId, auditoriumId, start, start.plusSeconds(3_600),
			100_000));

		assertThatThrownBy(() -> movieRepository.deleteById(movieId)).isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> auditoriumRepository.deleteById(auditoriumId)).isInstanceOf(ConflictException.class);
		assertThat(movies.get(movieId)).isNotNull();
		assertThat(auditoriums.get(auditoriumId)).isNotNull();
	}

	@Test
	void cancelledShowtimeKeepsItsReasonAndNote() {
		UUID movieId = movies.create("Film", "Synopsis", 90, "Drama", AgeRating.P,
			LocalDate.parse("2026-09-01"), "poster").id();
		UUID cinemaId = cinemas.create("Cinema " + UUID.randomUUID(), "Address").id();
		UUID auditoriumId = auditoriums.create(cinemaId, "Hall", 1, 1, List.of(), List.of()).id();
		Instant start = Instant.parse("2026-10-01T12:00:00Z");
		Showtime showtime = showtimes.create(movieId, auditoriumId, start, 100_000);
		Cancellation cancellation = new Cancellation(CancellationReason.OTHER, "Flooded auditorium");

		assertThat(repository.cancel(showtime.id(), cancellation, start.minusSeconds(3_600))).isTrue();

		Showtime cancelled = showtimes.get(showtime.id());
		assertThat(cancelled.status()).isEqualTo(ShowtimeStatus.CANCELLED);
		assertThat(cancelled.cancellation()).isEqualTo(cancellation);
	}

	@Test
	void showtimeCanBeCancelledOnlyOnceAndOnlyUntilItEnds() {
		UUID movieId = movies.create("Film", "Synopsis", 90, "Drama", AgeRating.P,
			LocalDate.parse("2026-09-01"), "poster").id();
		UUID cinemaId = cinemas.create("Cinema " + UUID.randomUUID(), "Address").id();
		UUID auditoriumId = auditoriums.create(cinemaId, "Hall", 1, 1, List.of(), List.of()).id();
		Showtime ended = showtimes.create(movieId, auditoriumId, Instant.parse("2026-10-01T12:00:00Z"), 100_000);
		Showtime running = showtimes.create(movieId, auditoriumId, ended.endsAt(), 100_000);
		Cancellation powerCut = new Cancellation(CancellationReason.POWER_OUTAGE, null);

		assertThat(repository.cancel(ended.id(), powerCut, ended.endsAt())).isFalse();
		assertThat(showtimes.get(ended.id()).status()).isEqualTo(ShowtimeStatus.SCHEDULED);

		Instant midway = running.startsAt().plusSeconds(1_800);
		assertThat(repository.cancel(running.id(), powerCut, midway)).isTrue();
		assertThat(repository.cancel(running.id(), new Cancellation(CancellationReason.EMERGENCY, null), midway))
			.isFalse();
		assertThat(showtimes.get(running.id()).cancellation()).isEqualTo(powerCut);
	}

	@Test
	void cancelledShowtimeNoLongerHoldsItsSlotInTheDatabase() {
		UUID movieId = movies.create("Film", "Synopsis", 90, "Drama", AgeRating.P,
			LocalDate.parse("2026-09-01"), "poster").id();
		UUID cinemaId = cinemas.create("Cinema " + UUID.randomUUID(), "Address").id();
		UUID auditoriumId = auditoriums.create(cinemaId, "Hall", 1, 1, List.of(), List.of()).id();
		Showtime cancelled = showtimes.create(movieId, auditoriumId, Instant.parse("2026-10-01T12:00:00Z"), 100_000);
		repository.cancel(cancelled.id(), new Cancellation(CancellationReason.POWER_OUTAGE, null),
			cancelled.startsAt());

		Showtime replacement = repository.save(new Showtime(UUID.randomUUID(), movieId, auditoriumId,
			cancelled.startsAt(), cancelled.endsAt(), 100_000));

		assertThat(showtimes.get(replacement.id())).isEqualTo(replacement);
	}

	@Test
	void replacementShowtimeCanTakeACancelledOnesSlot() {
		UUID movieId = movies.create("Film", "Synopsis", 90, "Drama", AgeRating.P,
			LocalDate.parse("2026-09-01"), "poster").id();
		UUID cinemaId = cinemas.create("Cinema " + UUID.randomUUID(), "Address").id();
		UUID auditoriumId = auditoriums.create(cinemaId, "Hall", 1, 1, List.of(), List.of()).id();
		Showtime cancelled = showtimes.create(movieId, auditoriumId, Instant.parse("2026-10-01T12:00:00Z"), 100_000);
		showtimes.cancel(cancelled.id(), CancellationReason.EMERGENCY, null, cancelled.startsAt());

		Showtime replacement = showtimes.create(movieId, auditoriumId, cancelled.startsAt().plusSeconds(1_800),
			100_000);

		assertThat(showtimes.get(replacement.id()).status()).isEqualTo(ShowtimeStatus.SCHEDULED);
	}

	@Test
	void cancelledShowtimeIsLeftOutOfTheListing() {
		UUID movieId = movies.create("Film", "Synopsis", 90, "Drama", AgeRating.P,
			LocalDate.parse("2026-09-01"), "poster").id();
		UUID cinemaId = cinemas.create("Cinema " + UUID.randomUUID(), "Address").id();
		UUID auditoriumId = auditoriums.create(cinemaId, "Hall", 1, 1, List.of(), List.of()).id();
		Instant start = Instant.parse("2026-10-01T12:00:00Z");
		Showtime cancelled = showtimes.create(movieId, auditoriumId, start, 100_000);
		showtimes.cancel(cancelled.id(), CancellationReason.EMERGENCY, null, start);
		Showtime showing = showtimes.create(movieId, auditoriumId, start, 100_000);

		assertThat(showtimes.list(start, start.plusSeconds(1), cinemaId, movieId)).containsExactly(showing);
	}

	@Test
	void savesAndFiltersByStartingTimeCinemaAndMovie() {
		UUID movieId = movies.create("Film", "Synopsis", 90, "Drama", AgeRating.P, LocalDate.parse("2026-09-01"), "poster").id();
		UUID otherMovieId = movies.create("Other", "Synopsis", 90, "Drama", AgeRating.P, LocalDate.parse("2026-09-01"), "poster").id();
		UUID cinemaId = cinemas.create("Cinema " + UUID.randomUUID(), "Address").id();
		UUID otherCinemaId = cinemas.create("Cinema " + UUID.randomUUID(), "Address").id();
		Auditorium hall = auditoriums.create(cinemaId, "Hall", 1, 1, List.of(), List.of());
		Auditorium otherHall = auditoriums.create(otherCinemaId, "Hall", 1, 1, List.of(), List.of());
		Instant start = Instant.parse("2026-10-01T17:00:00Z"); // 00:00 +07 on Oct 2
		Showtime expected = showtimes.create(movieId, hall.id(), start, 100_000);
		showtimes.create(otherMovieId, hall.id(), start.plusSeconds(10_800), 120_000);
		showtimes.create(movieId, otherHall.id(), start.plusSeconds(10_800), 100_000);

		assertThat(showtimes.get(expected.id())).isEqualTo(expected);
		assertThat(showtimes.list(start, start.plusSeconds(1), cinemaId, movieId)).containsExactly(expected);
		assertThat(showtimes.list(start.plusSeconds(1), start.plusSeconds(10_800), cinemaId, movieId)).isEmpty();
		showtimes.delete(expected.id(), Instant.now());
		assertThat(showtimes.list(start, start.plusSeconds(1), cinemaId, movieId)).isEmpty();
	}
}
