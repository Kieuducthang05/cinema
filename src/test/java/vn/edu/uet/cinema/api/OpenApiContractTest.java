package vn.edu.uet.cinema.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.AnnotatedClassFinder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.uet.cinema.CinemaApplication;
import vn.edu.uet.cinema.business.account.AccountService;
import vn.edu.uet.cinema.business.auditorium.AuditoriumService;
import vn.edu.uet.cinema.business.booking.BookingService;
import vn.edu.uet.cinema.business.cinema.CinemaService;
import vn.edu.uet.cinema.business.movie.MovieService;
import vn.edu.uet.cinema.business.showtime.SeatOccupancyReader;
import vn.edu.uet.cinema.business.showtime.ShowtimeService;
import vn.edu.uet.cinema.config.OpenApiConfig;
import vn.edu.uet.cinema.config.SecurityConfig;

@SpringBootTest(classes = OpenApiContractTest.TestApplication.class,
	properties = "app.jwt.secret=issue42-test-secret-with-at-least-32-bytes")
@AutoConfigureMockMvc
class OpenApiContractTest {

	private static final String PROBLEM = "application/problem+json";
	private static final Map<String, String> SUCCESS_RESPONSES = Map.ofEntries(
		Map.entry("POST /api/auth/register", "201"),
		Map.entry("POST /api/cinemas", "201"),
		Map.entry("DELETE /api/cinemas/{id}", "204"),
		Map.entry("POST /api/cinemas/{id}/auditoriums", "201"),
		Map.entry("DELETE /api/auditoriums/{id}", "204"),
		Map.entry("POST /api/movies", "201"),
		Map.entry("DELETE /api/movies/{id}", "204"),
		Map.entry("POST /api/showtimes", "201"),
		Map.entry("DELETE /api/showtimes/{id}", "204"),
		Map.entry("POST /api/bookings", "201"),
		Map.entry("DELETE /api/bookings/{id}", "204"));
	private static final Map<String, Set<String>> ERROR_MATRIX = Map.ofEntries(
		Map.entry("POST /api/auth/register", Set.of("400", "401", "409")),
		Map.entry("POST /api/auth/login", Set.of("400", "401")),
		Map.entry("GET /api/me", Set.of("401", "404")),
		Map.entry("POST /api/cinemas", Set.of("400", "401", "403")),
		Map.entry("GET /api/cinemas", Set.of("401")),
		Map.entry("GET /api/cinemas/{id}", Set.of("400", "401", "404")),
		Map.entry("PUT /api/cinemas/{id}", Set.of("400", "401", "403", "404")),
		Map.entry("DELETE /api/cinemas/{id}", Set.of("400", "401", "403", "404", "409")),
		Map.entry("POST /api/cinemas/{id}/auditoriums", Set.of("400", "401", "403", "404", "409")),
		Map.entry("GET /api/cinemas/{id}/auditoriums", Set.of("400", "401", "404")),
		Map.entry("GET /api/auditoriums/{id}", Set.of("400", "401", "404")),
		Map.entry("DELETE /api/auditoriums/{id}", Set.of("400", "401", "403", "404", "409")),
		Map.entry("POST /api/movies", Set.of("400", "401", "403")),
		Map.entry("GET /api/movies", Set.of("401")),
		Map.entry("GET /api/movies/{id}", Set.of("400", "401", "404")),
		Map.entry("PUT /api/movies/{id}", Set.of("400", "401", "403", "404")),
		Map.entry("DELETE /api/movies/{id}", Set.of("400", "401", "403", "404", "409")),
		Map.entry("POST /api/showtimes", Set.of("400", "401", "403", "404", "409")),
		Map.entry("GET /api/showtimes", Set.of("400", "401")),
		Map.entry("GET /api/showtimes/{id}", Set.of("400", "401", "404")),
		Map.entry("GET /api/showtimes/{id}/seats", Set.of("400", "401", "404", "409")),
		Map.entry("POST /api/showtimes/{id}/cancellation", Set.of("400", "401", "403", "404", "409")),
		Map.entry("DELETE /api/showtimes/{id}", Set.of("400", "401", "403", "404", "409")),
		Map.entry("POST /api/bookings", Set.of("400", "401", "403", "404", "409")),
		Map.entry("GET /api/bookings", Set.of("401", "403")),
		Map.entry("GET /api/bookings/{id}", Set.of("400", "401", "403", "404")),
		Map.entry("POST /api/bookings/{id}/payment", Set.of("400", "401", "403", "404", "409")),
		Map.entry("DELETE /api/bookings/{id}", Set.of("400", "401", "403", "404", "409")));

	@Autowired
	MockMvc mvc;

	@MockitoBean
	AccountService accounts;
	@MockitoBean
	CinemaService cinemas;
	@MockitoBean
	AuditoriumService auditoriums;
	@MockitoBean
	MovieService movies;
	@MockitoBean
	ShowtimeService showtimes;
	@MockitoBean
	SeatOccupancyReader occupancy;
	@MockitoBean
	BookingService bookings;

	@Test
	void yamlSpecificationIsPublic() throws Exception {
		mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
	}

	@Test
	void generatedOpenApiCoversAllOperationsErrorsSecurityAndBounds() throws Exception {
		String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		Map<String, Object> api = JsonPath.read(json, "$");
		Map<String, Object> paths = map(api.get("paths"));
		Map<String, Object> components = map(api.get("components"));
		Map<String, Object> responses = map(components.get("responses"));
		Map<String, Object> schemas = map(components.get("schemas"));
		Map<String, Object> validationProblem = map(schemas.get("ValidationProblem"));
		Map<String, Object> properties = map(validationProblem.get("properties"));

		assertThat(ERROR_MATRIX).hasSize(28);
		assertThat(paths.values().stream().map(OpenApiContractTest::map).mapToInt(path -> (int) path.keySet().stream()
			.filter(Set.of("get", "post", "put", "delete")::contains).count()).sum()).isEqualTo(28);
		assertThat(api.get("security")).isNull();
		assertThat(properties).containsKeys("type", "title", "status", "detail", "instance", "errors", "seatIds");
		assertThat(new HashSet<>((List<?>) validationProblem.get("required")))
			.isEqualTo(Set.of("title", "status"));
		assertThat(map(properties.get("type")).get("default")).isEqualTo("about:blank");
		assertThat(map(properties.get("errors")).get("items")).isNotNull();

		int secured = 0;
		Set<String> tags = new HashSet<>();
		for (Map.Entry<String, Set<String>> expected : ERROR_MATRIX.entrySet()) {
			int separator = expected.getKey().indexOf(' ');
			String method = expected.getKey().substring(0, separator).toLowerCase();
			String path = expected.getKey().substring(separator + 1);
			Map<String, Object> operation = map(map(paths.get(path)).get(method));
			assertThat(operation.get("summary")).as(expected.getKey()).isNotNull().isNotEqualTo("");
			assertThat(operation.get("description")).as(expected.getKey()).isNotNull().isNotEqualTo("");
			for (Object tag : (List<?>) operation.get("tags")) {
				tags.add(tag.toString());
			}
			Map<String, Object> operationResponses = map(operation.get("responses"));
			Set<String> actualErrors = operationResponses.keySet().stream().filter(code -> code.startsWith("4"))
				.collect(java.util.stream.Collectors.toSet());
			assertThat(actualErrors).as(expected.getKey()).isEqualTo(expected.getValue());
			assertThat(operationResponses.keySet().stream().filter(code -> code.startsWith("2"))
				.collect(java.util.stream.Collectors.toSet())).as(expected.getKey())
				.isEqualTo(Set.of(SUCCESS_RESPONSES.getOrDefault(expected.getKey(), "200")));
			assertThat(operationResponses.keySet()).noneMatch(code -> code.startsWith("5"));
			for (String code : expected.getValue()) {
				Map<String, Object> response = resolveResponse(map(operationResponses.get(code)), responses);
				Map<String, Object> content = map(response.get("content"));
				assertThat(content).as(expected.getKey() + " " + code).containsKey(PROBLEM);
				Map<String, Object> problemContent = map(content.get(PROBLEM));
				assertThat(map(problemContent.get("schema")).get("$ref"))
					.isEqualTo("#/components/schemas/ValidationProblem");
				Map<String, Object> examples = map(problemContent.get("examples"));
				assertThat(examples).as(expected.getKey() + " " + code).isNotEmpty();
				for (Object example : examples.values()) {
					Map<String, Object> value = map(map(example).get("value"));
					assertThat(value.get("status")).as(expected.getKey() + " example status").isEqualTo(Integer.valueOf(code));
					assertThat(value.get("type")).isEqualTo("about:blank");
					assertThat(value.get("title")).isNotNull();
					if (value.containsKey("errors")) {
						for (Object error : (List<?>) value.get("errors")) {
							assertThat(map(error).keySet()).containsExactlyInAnyOrder("field", "message");
						}
					}
				}
			}
			boolean shouldRequireBearer = path.equals("/api/me") || path.startsWith("/api/bookings")
				|| !method.equals("get") && !path.startsWith("/api/auth/");
			assertThat(operation.containsKey("security")).as(expected.getKey()).isEqualTo(shouldRequireBearer);
			if (shouldRequireBearer) {
				secured++;
			}
		}
		assertThat(secured).isEqualTo(17);
		assertThat(tags).containsExactlyInAnyOrder("Accounts", "Cinemas", "Auditoriums", "Movies", "Showtimes", "Bookings");
		assertSecurityProblemExamples(responses);

		assertRequestExamples(schemas, "RegisterRequest", "email", "password", "fullName");
		assertRequestExamples(schemas, "LoginRequest", "email", "password");
		assertRequestExamples(schemas, "CinemaRequest", "name", "address");
		assertRequestExamples(schemas, "AuditoriumRequest", "name", "rows", "seatsPerRow", "vipRows", "coupleRows");
		assertRequestExamples(schemas, "MovieRequest", "title", "description", "durationMinutes", "genre", "ageRating",
			"releaseDate", "posterUrl");
		assertRequestExamples(schemas, "ShowtimeRequest", "movieId", "auditoriumId", "startsAt", "basePrice");
		assertRequestExamples(schemas, "CancellationRequest", "reason", "note");
		assertRequestExamples(schemas, "BookingRequest", "showtimeId", "seatIds");

		assertThat(map(map(schemas.get("RegisterRequest")).get("properties")).get("password"))
			.satisfies(value -> {
				Map<String, Object> password = map(value);
				assertThat(password.get("minLength")).isEqualTo(8);
				assertThat(password).doesNotContainKey("maxLength").containsKey("example");
			});
		assertNoStorageCeilings(schemas, "RegisterRequest", "email", "fullName");
		assertNoStorageCeilings(schemas, "RegisterRequest", "password");
		assertNoStorageCeilings(schemas, "CinemaRequest", "name", "address");
		assertNoStorageCeilings(schemas, "AuditoriumRequest", "name");
		assertNoStorageCeilings(schemas, "MovieRequest", "title", "genre", "description", "posterUrl");
		assertProperty(schemas, "RegisterRequest", "email", "minLength", 1);
		assertProperty(schemas, "RegisterRequest", "fullName", "minLength", 1);
		assertProperty(schemas, "CinemaRequest", "name", "minLength", 1);
		assertProperty(schemas, "AuditoriumRequest", "name", "minLength", 1);
		assertProperty(schemas, "MovieRequest", "title", "minLength", 1);
		assertProperty(schemas, "MovieRequest", "genre", "minLength", 1);
		assertProperty(schemas, "AuditoriumRequest", "rows", "minimum", 1);
		assertProperty(schemas, "AuditoriumRequest", "rows", "maximum", 26);
		assertProperty(schemas, "AuditoriumRequest", "seatsPerRow", "minimum", 1);
		assertProperty(schemas, "BookingRequest", "seatIds", "minItems", 1);
		assertProperty(schemas, "BookingRequest", "seatIds", "maxItems", 8);
		assertProperty(schemas, "MovieRequest", "durationMinutes", "minimum", 1);
		assertProperty(schemas, "ShowtimeRequest", "basePrice", "minimum", 1);
		assertProperty(schemas, "CancellationRequest", "note", "maxLength", 200);
		assertProperty(schemas, "BookedSeat", "seatId", "example", "550e8400-e29b-41d4-a716-446655440020");
		assertProperty(schemas, "BookedSeat", "price", "example", 100000);
	}

	@Test
	void actualProblemResponsesSatisfyTheOpenApiRequiredFields() throws Exception {
		Map<String, Object> api = JsonPath.read(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(), "$");
		Map<String, Object> validationProblem = map(map(map(api.get("components")).get("schemas")).get("ValidationProblem"));
		Set<String> required = ((List<?>) validationProblem.get("required")).stream()
			.map(Object::toString).collect(java.util.stream.Collectors.toSet());
		assertThat(required).isEqualTo(Set.of("title", "status"));
		assertThat(map(map(validationProblem.get("properties")).get("type")).get("default")).isEqualTo("about:blank");

		String registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"not-an-email\",\"password\":\"short\",\"fullName\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andReturn().getResponse().getContentAsString();
		String invalidUuid = mvc.perform(get("/api/movies/not-a-uuid"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andReturn().getResponse().getContentAsString();
		String invalidDate = mvc.perform(get("/api/showtimes").param("date", "DATE_CANARY"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andReturn().getResponse().getContentAsString();

		for (String body : List.of(registration, invalidUuid, invalidDate)) {
			Map<String, Object> problem = JsonPath.read(body, "$");
			assertThat(problem.keySet()).containsAll(required);
			assertThat(problem.get("status")).isEqualTo(400);
		}
		Map<String, Object> registrationProblem = JsonPath.read(registration, "$");
		List<Map<String, Object>> registrationErrors = JsonPath.read(registration, "$.errors");
		assertThat(registrationProblem.get("detail")).isEqualTo("Request validation failed.");
		assertThat(registrationErrors).containsExactlyInAnyOrder(
			Map.of("field", "email", "message", "Email must be valid"),
			Map.of("field", "password", "message", "Password must be at least 8 characters"),
			Map.of("field", "fullName", "message", "Full name is required"));
		Map<String, Object> uuidProblem = JsonPath.read(invalidUuid, "$");
		List<Map<String, Object>> uuidErrors = JsonPath.read(invalidUuid, "$.errors");
		assertThat(uuidProblem.get("detail")).isEqualTo("A request value has an invalid type.");
		assertThat(uuidProblem.get("instance")).isEqualTo("/api/movies/not-a-uuid");
		assertThat(uuidErrors).containsExactly(Map.of("field", "id", "message", "Must be a valid UUID."));
		Map<String, Object> dateProblem = JsonPath.read(invalidDate, "$");
		List<Map<String, Object>> dateErrors = JsonPath.read(invalidDate, "$.errors");
		assertThat(dateProblem.get("detail")).isEqualTo("A request value has an invalid type.");
		assertThat(dateErrors).containsExactly(
			Map.of("field", "date", "message", "Must be an ISO-8601 date (yyyy-MM-dd)."));
	}

	@Test
	void nestedTestConfigurationDoesNotReplaceTheApplicationConfiguration() {
		assertThat(new AnnotatedClassFinder(SpringBootConfiguration.class)
			.findFromPackage("vn.edu.uet.cinema.api.account")).isEqualTo(CinemaApplication.class);
	}

	@Test
	void publicCatalogueAllowsAnonymousRequestsButRejectsAnInvalidBearerToken() throws Exception {
		when(cinemas.list()).thenReturn(List.of());
		mvc.perform(get("/api/cinemas")).andExpect(status().isOk());
		mvc.perform(get("/api/cinemas").header("Authorization", "Bearer invalid"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.type").value("about:blank"))
			.andExpect(jsonPath("$.title").value("Unauthorized"))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.detail").doesNotExist())
			.andExpect(jsonPath("$.errors").doesNotExist());
	}

	private static Map<String, Object> resolveResponse(Map<String, Object> response, Map<String, Object> components) {
		Object ref = response.get("$ref");
		if (ref == null) {
			return response;
		}
		String name = ref.toString().substring("#/components/responses/".length());
		return map(components.get(name));
	}

	private static void assertNoStorageCeilings(Map<String, Object> schemas, String schema, String... fields) {
		Map<String, Object> properties = map(map(schemas.get(schema)).get("properties"));
		for (String field : fields) {
			assertThat(map(properties.get(field))).as(schema + "." + field).doesNotContainKey("maxLength");
		}
	}

	private static void assertRequestExamples(Map<String, Object> schemas, String schema, String... fields) {
		Map<String, Object> properties = map(map(schemas.get(schema)).get("properties"));
		for (String field : fields) {
			Map<String, Object> property = map(properties.get(field));
			assertThat(property.containsKey("example") || property.containsKey("items")
				&& map(property.get("items")).containsKey("example"))
				.as(schema + "." + field + " should have an OpenAPI example").isTrue();
		}
	}

	private static void assertSecurityProblemExamples(Map<String, Object> responses) {
		Map<String, Object> unauthorized = map(map(map(responses.get("Unauthorized")).get("content")).get(PROBLEM));
		Map<String, Object> forbidden = map(map(map(responses.get("Forbidden")).get("content")).get(PROBLEM));
		assertThat(map(map(unauthorized.get("examples")).get("invalidBearer")).get("value"))
			.isEqualTo(Map.of("type", "about:blank", "title", "Unauthorized", "status", 401));
		assertThat(map(map(forbidden.get("examples")).get("roleRequired")).get("value"))
			.isEqualTo(Map.of("type", "about:blank", "title", "Forbidden", "status", 403));
		Map<String, Object> conflict = map(map(map(responses.get("Conflict")).get("content")).get(PROBLEM));
		Map<String, Object> seatTaken = map(map(map(conflict.get("examples")).get("seatTaken")).get("value"));
		assertThat(seatTaken).containsKey("seatIds").doesNotContainKey("errors");
		assertThat(map(map(conflict.get("examples")).get("cancellationConflict")).get("value"))
			.isEqualTo(Map.of("type", "about:blank", "title", "Conflict", "status", 409,
				"detail", "Showtime is already Cancelled or has ended"));
	}

	private static void assertProperty(Map<String, Object> schemas, String schema, String field, String property,
		Object expected) {
		assertThat(map(map(map(schemas.get(schema)).get("properties")).get(field)).get(property))
			.as(schema + "." + field + "." + property).isEqualTo(expected);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object value) {
		return (Map<String, Object>) value;
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(excludeName = {
		"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
		"org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"})
	@ComponentScan(basePackages = {
		"vn.edu.uet.cinema.api.account",
		"vn.edu.uet.cinema.api.auditorium",
		"vn.edu.uet.cinema.api.booking",
		"vn.edu.uet.cinema.api.cinema",
		"vn.edu.uet.cinema.api.movie",
		"vn.edu.uet.cinema.api.showtime"})
	@Import({ApiExceptionHandler.class, OpenApiConfig.class, SecurityConfig.class})
	static class TestApplication {
	}
}
