package vn.edu.uet.cinema.api.showtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.uet.cinema.TestcontainersConfiguration;
import vn.edu.uet.cinema.business.auditorium.AuditoriumService;
import vn.edu.uet.cinema.business.cinema.CinemaService;
import vn.edu.uet.cinema.business.movie.AgeRating;
import vn.edu.uet.cinema.business.movie.MovieService;
import vn.edu.uet.cinema.business.showtime.ShowtimeService;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShowtimeApiTest {

	@Autowired MockMvc mvc;
	@Autowired MovieService movies;
	@Autowired CinemaService cinemas;
	@Autowired AuditoriumService auditoriums;
	@Autowired ShowtimeService showtimes;

	@Test
	void adminSchedulesAndDeletesShowtimeWithOffsetConvertedToInstant() throws Exception {
		String admin = token("admin@cinema.local", "admin12345");
		UUID movieId = movieId();
		UUID auditoriumId = auditoriumId();
		String created = mvc.perform(post("/api/showtimes").header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(showtimeJson(movieId, auditoriumId, "2026-09-27T20:00:00+07:00", 100_000)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.movieId").value(movieId.toString()))
			.andExpect(jsonPath("$.auditoriumId").value(auditoriumId.toString()))
			.andExpect(jsonPath("$.startsAt").value("2026-09-27T13:00:00Z"))
			.andExpect(jsonPath("$.basePrice").value(100_000))
			.andExpect(jsonPath("$.status").value("SCHEDULED"))
			.andExpect(jsonPath("$.cancellation").value(nullValue()))
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(created, "$.id");

		mvc.perform(delete("/api/showtimes/" + id).header("Authorization", "Bearer " + admin))
			.andExpect(status().isNoContent());
	}

	@Test
	void adminCancelsShowtimeAndItKeepsItsCancellationReason() throws Exception {
		String admin = token("admin@cinema.local", "admin12345");
		UUID id = schedule(movieId(), auditoriumId(), Instant.now().plusSeconds(86_400));

		mvc.perform(post("/api/showtimes/" + id + "/cancellation").header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"EQUIPMENT_FAILURE\",\"note\":\"Projector lamp blew\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id.toString()))
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.cancellation.reason").value("EQUIPMENT_FAILURE"))
			.andExpect(jsonPath("$.cancellation.note").value("Projector lamp blew"));

		mvc.perform(get("/api/showtimes/" + id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.cancellation.reason").value("EQUIPMENT_FAILURE"));
	}

	@Test
	void adminSchedulesAReplacementInACancelledShowtimesSlot() throws Exception {
		String admin = token("admin@cinema.local", "admin12345");
		UUID movieId = movieId();
		UUID auditoriumId = auditoriumId();
		Instant startsAt = Instant.now().plusSeconds(86_400).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
		UUID id = schedule(movieId, auditoriumId, startsAt);
		String replacement = showtimeJson(movieId, auditoriumId, startsAt.plusSeconds(1_800).toString(), 100_000);
		mvc.perform(post("/api/showtimes").header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content(replacement))
			.andExpect(status().isConflict());

		mvc.perform(post("/api/showtimes/" + id + "/cancellation").header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"POWER_OUTAGE\"}"))
			.andExpect(status().isOk());

		mvc.perform(post("/api/showtimes").header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content(replacement))
			.andExpect(status().isCreated());
	}

	@Test
	void cancelledShowtimeHasNoSeatMap() throws Exception {
		String admin = token("admin@cinema.local", "admin12345");
		UUID id = schedule(movieId(), auditoriumId(), Instant.now().plusSeconds(86_400));
		mvc.perform(post("/api/showtimes/" + id + "/cancellation").header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"EQUIPMENT_FAILURE\"}"))
			.andExpect(status().isOk());

		mvc.perform(get("/api/showtimes/" + id + "/seats"))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void showtimeWritesRequireAdmin() throws Exception {
		String body = showtimeJson(UUID.randomUUID(), UUID.randomUUID(), "2026-09-27T20:00:00+07:00", 100_000);
		mvc.perform(post("/api/showtimes").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

		String email = "showtime-customer-" + UUID.randomUUID() + "@example.com";
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"Customer\"}".formatted(email)))
			.andExpect(status().isCreated());
		String customer = token(email, "password123");
		mvc.perform(post("/api/showtimes").header("Authorization", "Bearer " + customer)
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isForbidden())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

		String cancellation = "{\"reason\":\"EMERGENCY\"}";
		String path = "/api/showtimes/" + UUID.randomUUID() + "/cancellation";
		mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(cancellation))
			.andExpect(status().isUnauthorized());
		mvc.perform(post(path).header("Authorization", "Bearer " + customer)
				.contentType(MediaType.APPLICATION_JSON).content(cancellation))
			.andExpect(status().isForbidden());
	}

	@Test
	void invalidShowtimeRequestIsProblemDetail() throws Exception {
		String admin = token("admin@cinema.local", "admin12345");
		mvc.perform(post("/api/showtimes").header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(showtimeJson(UUID.randomUUID(), UUID.randomUUID(), "2026-09-27T20:00:00+07:00", 0)))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void invalidCancellationIsBadRequestAndCancellingTwiceIsConflict() throws Exception {
		String admin = token("admin@cinema.local", "admin12345");
		UUID id = schedule(movieId(), auditoriumId(), Instant.now().plusSeconds(86_400));
		String path = "/api/showtimes/" + id + "/cancellation";

		String rejectedReason = "UNKNOWN_REASON_CANARY";
		String problem = mvc.perform(post(path).header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + rejectedReason + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[0].field").value("reason"))
			.andReturn().getResponse().getContentAsString();
		String message = JsonPath.read(problem, "$.errors[0].message");
		assertThat(message).contains("EQUIPMENT_FAILURE", "POWER_OUTAGE", "EMERGENCY", "OTHER");
		assertThat(problem).doesNotContain(rejectedReason);

		for (String body : List.of("{}", "{\"reason\":\"OTHER\"}",
			"{\"reason\":\"OTHER\",\"note\":\"  \"}")) {
			mvc.perform(post(path).header("Authorization", "Bearer " + admin)
					.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		}
		mvc.perform(post(path).header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"POWER_OUTAGE\"}"))
			.andExpect(status().isOk());
		mvc.perform(post(path).header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"POWER_OUTAGE\"}"))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		mvc.perform(post("/api/showtimes/" + UUID.randomUUID() + "/cancellation")
				.header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"POWER_OUTAGE\"}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void showtimeWriteOperationsAreMarkedProtectedInOpenApi() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/showtimes'].post.security[0].bearerAuth").exists())
			.andExpect(jsonPath("$.paths['/api/showtimes/{id}'].delete.security[0].bearerAuth").exists())
			.andExpect(jsonPath("$.paths['/api/showtimes/{id}/cancellation'].post.security[0].bearerAuth").exists());
	}

	@Test
	void showtimesByDateUseVietnameseMidnightBoundariesAndOptionalFilters() throws Exception {
		UUID movie1 = movieId();
		UUID movie2 = movieId();
		UUID cinema1 = cinemas.create("Cinema " + UUID.randomUUID(), "Address").id();
		UUID cinema2 = cinemas.create("Cinema " + UUID.randomUUID(), "Address").id();
		UUID beforeMidnight = schedule(movie1, auditoriumId(cinema1), Instant.parse("2026-10-01T16:59:59.999999Z"));
		UUID midnight = schedule(movie1, auditoriumId(cinema1), Instant.parse("2026-10-01T17:00:00Z"));
		UUID lastMoment = schedule(movie2, auditoriumId(cinema2), Instant.parse("2026-10-02T16:59:59.999999Z"));
		UUID nextMidnight = schedule(movie1, auditoriumId(cinema2), Instant.parse("2026-10-02T17:00:00Z"));
		UUID otherShowtime = schedule(movieId(), auditoriumId(), Instant.parse("2026-10-02T05:00:00Z"));

		// API tests share a Testcontainers database, so assert membership rather than a global result count.
		mvc.perform(get("/api/showtimes").param("date", "2026-10-02"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].id").value(hasItem(midnight.toString())))
			.andExpect(jsonPath("$[*].id").value(hasItem(lastMoment.toString())))
			.andExpect(jsonPath("$[*].id").value(hasItem(otherShowtime.toString())))
			.andExpect(jsonPath("$[*].id").value(not(hasItem(beforeMidnight.toString()))))
			.andExpect(jsonPath("$[*].id").value(not(hasItem(nextMidnight.toString()))));
		mvc.perform(get("/api/showtimes").param("date", "2026-10-02").param("cinemaId", cinema1.toString()))
			.andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(midnight.toString()));
		mvc.perform(get("/api/showtimes").param("date", "2026-10-02").param("cinemaId", cinema2.toString()))
			.andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(lastMoment.toString()));
		mvc.perform(get("/api/showtimes").param("date", "2026-10-02").param("movieId", movie2.toString()))
			.andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(lastMoment.toString()));
		mvc.perform(get("/api/showtimes").param("date", "2026-10-02").param("cinemaId", cinema1.toString())
				.param("movieId", movie1.toString()))
			.andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(midnight.toString()));
		mvc.perform(get("/api/showtimes/" + midnight)).andExpect(status().isOk())
			.andExpect(jsonPath("$.startsAt").value("2026-10-01T17:00:00Z"));
		mvc.perform(get("/api/showtimes")).andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void seatMapUsesAuditoriumLayoutAndShowsCurrentPricesAndAvailability() throws Exception {
		UUID cinemaId = cinemas.create("Cinema " + UUID.randomUUID(), "Address").id();
		UUID auditoriumId = auditoriums.create(cinemaId, "Seat map hall", 3, 2, List.of("B"), List.of("C")).id();
		UUID showtimeId = showtimes.create(movieId(), auditoriumId, Instant.parse("2026-11-05T17:00:00Z"),
			100_000).id();

		mvc.perform(get("/api/showtimes/" + showtimeId + "/seats"))
			.andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(6)))
			.andExpect(jsonPath("$[*].status").value(contains("AVAILABLE", "AVAILABLE", "AVAILABLE", "AVAILABLE",
				"AVAILABLE", "AVAILABLE")))
			.andExpect(jsonPath("$[0].label").value("A1"))
			.andExpect(jsonPath("$[0].type").value("STANDARD"))
			.andExpect(jsonPath("$[0].price").value(100_000))
			.andExpect(jsonPath("$[2].label").value("B1"))
			.andExpect(jsonPath("$[2].type").value("VIP"))
			.andExpect(jsonPath("$[2].price").value(120_000))
			.andExpect(jsonPath("$[4].label").value("C1"))
			.andExpect(jsonPath("$[4].type").value("COUPLE"))
			.andExpect(jsonPath("$[4].price").value(210_000));
	}

	private UUID movieId() {
		return movies.create("Film " + UUID.randomUUID(), "Synopsis", 120, "Drama", AgeRating.P,
			LocalDate.parse("2026-09-01"), "poster").id();
	}

	private UUID auditoriumId() {
		return auditoriumId(cinemas.create("Cinema " + UUID.randomUUID(), "Address").id());
	}

	private UUID auditoriumId(UUID cinemaId) {
		return auditoriums.create(cinemaId, "Hall " + UUID.randomUUID(), 1, 1, List.of(), List.of()).id();
	}

	private UUID schedule(UUID movieId, UUID auditoriumId, Instant startsAt) {
		return showtimes.create(movieId, auditoriumId, startsAt, 100_000).id();
	}

	private String token(String email, String password) throws Exception {
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	private static String showtimeJson(UUID movieId, UUID auditoriumId, String startsAt, long basePrice) {
		return "{\"movieId\":\"%s\",\"auditoriumId\":\"%s\",\"startsAt\":\"%s\",\"basePrice\":%d}"
			.formatted(movieId, auditoriumId, startsAt, basePrice);
	}
}
