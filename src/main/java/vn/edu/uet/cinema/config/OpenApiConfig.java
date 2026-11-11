package vn.edu.uet.cinema.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI at /swagger-ui.html: log in, then paste the accessToken into "Authorize".
 * Protected operations declare {@code @SecurityRequirement(name = BEARER_AUTH)} themselves, so public ones show no lock.
 */
@Configuration
@OpenAPIDefinition(
	info = @Info(title = "Cinema Booking API", version = "v1",
		description = "Cinema chain backend: catalogue management and seat booking."))
@SecurityScheme(name = OpenApiConfig.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

	public static final String BEARER_AUTH = "bearerAuth";
	private static final String PROBLEM_SCHEMA = "#/components/schemas/ValidationProblem";
	private static final String PROBLEM_MEDIA_TYPE = "application/problem+json";
	private static final Map<String, Set<String>> ERROR_RESPONSES = Map.ofEntries(
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

	@Bean
	OpenApiCustomizer validationProblemSchema() {
		return openApi -> {
			addValidationProblemSchema(openApi);
			addProblemResponses(openApi.getComponents());
			documentRequiredStringMinimums(openApi);
			documentBookedSeat(openApi);
			hideStorageLimits(openApi);
			documentErrorResponses(openApi);
		};
	}

	private static void documentBookedSeat(OpenAPI openApi) {
		Map<String, Schema> schemas = openApi.getComponents().getSchemas();
		Schema<?> bookedSeat = schemas == null ? null : schemas.get("BookedSeat");
		if (bookedSeat == null || bookedSeat.getProperties() == null) {
			return;
		}
		Schema<?> seatId = (Schema<?>) bookedSeat.getProperties().get("seatId");
		seatId.setDescription("Seat identifier.");
		seatId.setExample("550e8400-e29b-41d4-a716-446655440020");
		Schema<?> price = (Schema<?>) bookedSeat.getProperties().get("price");
		price.setDescription("Seat price copied when the booking was created, in integer VND.");
		price.setExample(100000);
	}

	private static void documentRequiredStringMinimums(OpenAPI openApi) {
		Map<String, Schema> schemas = openApi.getComponents().getSchemas();
		if (schemas == null) {
			return;
		}
		Map<String, Set<String>> nonblankFields = Map.of(
			"RegisterRequest", Set.of("email", "fullName"),
			"LoginRequest", Set.of("email", "password"),
			"CinemaRequest", Set.of("name", "address"),
			"AuditoriumRequest", Set.of("name"),
			"MovieRequest", Set.of("title", "description", "genre", "posterUrl"));
		nonblankFields.forEach((name, fields) -> {
			Schema<?> schema = schemas.get(name);
			if (schema == null || schema.getProperties() == null) {
				return;
			}
			fields.forEach(field -> {
				Object property = schema.getProperties().get(field);
				if (property instanceof Schema<?> propertySchema) {
					propertySchema.setMinLength(1);
				}
			});
		});
	}

	private static void addValidationProblemSchema(OpenAPI openApi) {
		if (openApi.getComponents() == null) {
			openApi.setComponents(new Components());
		}
		ObjectSchema validationError = new ObjectSchema();
		validationError.addProperty("field", new StringSchema());
		validationError.addProperty("message", new StringSchema());
		validationError.setRequired(List.of("field", "message"));

		ArraySchema errors = new ArraySchema();
		errors.setItems(validationError);
		ObjectSchema problem = new ObjectSchema();
		StringSchema type = new StringSchema();
		type.setDefault("about:blank");
		problem.addProperty("type", type);
		problem.addProperty("title", new StringSchema());
		problem.addProperty("status", new IntegerSchema());
		problem.addProperty("detail", new StringSchema());
		problem.addProperty("instance", new StringSchema());
		problem.addProperty("errors", errors);
		ArraySchema seatIds = new ArraySchema();
		StringSchema seatId = new StringSchema();
		seatId.setFormat("uuid");
		seatIds.setItems(seatId);
		seatIds.setDescription("Seat IDs that were already held or sold; only present for seat-taken conflicts.");
		problem.addProperty("seatIds", seatIds);
		problem.setRequired(List.of("title", "status"));
		problem.setDescription("RFC 9457 problem details. Validation errors and seatIds are optional extensions.");
		openApi.getComponents().addSchemas("ValidationProblem", problem);
	}

	private static void addProblemResponses(Components components) {
		components.addResponses("BadRequest", problemResponse("Invalid body, parameter, or business-rule input.",
			Map.ofEntries(
				Map.entry("fieldValidation", validationExample("Bean validation", "Request validation failed.",
					"name", "Name is required")),
				Map.entry("invalidEnum", validationExample("Unknown enum value", "Request body contains an invalid value.",
					"ageRating", "Allowed values are [P, K, T13, T16, T18].")),
				Map.entry("invalidCancellationReason", validationExample("Unknown cancellation reason",
					"Request body contains an invalid value.", "reason",
					"Allowed values are [EQUIPMENT_FAILURE, POWER_OUTAGE, EMERGENCY, OTHER].")),
				Map.entry("invalidUuid", validationExample("Invalid UUID", "A request value has an invalid type.",
					"id", "Must be a valid UUID.")),
				Map.entry("invalidDate", validationExample("Invalid date", "A request value has an invalid type.",
					"date", "Must be an ISO-8601 date (yyyy-MM-dd).")),
				Map.entry("unreadableBody", example("Unreadable body", 400, "Request body could not be read.")),
				Map.entry("businessRule", example("Invalid base price", 400, "Base price must be positive")),
				Map.entry("invalidLayout", example("Invalid layout", 400, "Seat layout is not allowed")),
				Map.entry("duplicateSeats", example("Duplicate seat IDs", 400, "Duplicate seatIds")),
				Map.entry("cancellationNote", example("Cancellation note too long", 400,
					"A cancellation note has at most 200 characters")))));
		components.addResponses("LoginBadRequest", problemResponse("Login input is invalid.",
			Map.of("validation", example("Generic login validation", 400, "Invalid request."))));
		components.addResponses("Unauthorized", problemResponse("Authentication is required, credentials are invalid, "
			+ "or a supplied bearer token is invalid.", Map.of(
				"invalidBearer", example("Invalid bearer token", 401, null),
				"invalidCredentials", example("Invalid email or password", 401, "Invalid email or password"))));
		components.addResponses("Forbidden", problemResponse("The authenticated account does not have the role required "
			+ "for this operation.", Map.of("roleRequired", example("Insufficient role", 403, null))));
		components.addResponses("NotFound", problemResponse("The requested resource was not found.", Map.ofEntries(
			Map.entry("account", example("Account not found", 404, "Account not found")),
			Map.entry("cinema", example("Cinema not found", 404, "Cinema not found")),
			Map.entry("auditorium", example("Auditorium not found", 404, "Auditorium not found")),
			Map.entry("movie", example("Movie not found", 404, "Movie not found")),
			Map.entry("showtime", example("Showtime not found", 404, "Showtime not found")),
			Map.entry("booking", example("Booking not found", 404, "Booking not found")))));
		components.addResponses("Conflict", problemResponse("The request conflicts with the current business state.",
			Map.ofEntries(
				Map.entry("emailTaken", example("Email already registered", 409, "Email already registered")),
				Map.entry("resourceConflict", example("Showtime already cancelled", 409, "Showtime is Cancelled")),
				Map.entry("cancellationConflict", example("Showtime cancellation conflict", 409,
					"Showtime is already Cancelled or has ended")),
				Map.entry("cinemaHasAuditoriums", example("Cinema has auditoriums", 409, "Cinema has Auditoriums")),
				Map.entry("auditoriumHasShowtimes", example("Auditorium has showtimes", 409, "Auditorium has Showtimes")),
				Map.entry("movieHasShowtimes", example("Movie has showtimes", 409, "Movie has Showtimes")),
				Map.entry("auditoriumNameTaken", example("Auditorium name is already used", 409,
					"Auditorium name is already used in this Cinema")),
				Map.entry("showtimeHasBookings", example("Showtime has bookings", 409, "Showtime has Bookings")),
				Map.entry("seatTaken", example("Seat already taken", 409,
					"Seat already taken: [550e8400-e29b-41d4-a716-446655440000]",
					Map.of("seatIds", List.of("550e8400-e29b-41d4-a716-446655440000")))),
				Map.entry("bookingPending", example("Customer has a pending booking", 409,
					"Customer already has a Pending Booking 550e8400-e29b-41d4-a716-446655440000")),
				Map.entry("bookingState", example("Booking cannot be paid", 409,
					"Booking is EXPIRED, only a Pending Booking can be paid")))));
	}

	private static ApiResponse problemResponse(String description, Map<String, Example> examples) {
		MediaType problem = new MediaType().schema(new Schema<>().$ref(PROBLEM_SCHEMA));
		examples.forEach(problem::addExamples);
		return new ApiResponse().description(description)
			.content(new Content().addMediaType(PROBLEM_MEDIA_TYPE, problem));
	}

	private static Example example(String summary, int status, String detail) {
		return example(summary, problem(status, detail));
	}

	private static Example example(String summary, int status, String detail, Map<String, ?> extensions) {
		return example(summary, problem(status, detail, extensions));
	}

	private static Example validationExample(String summary, String detail, String field, String message) {
		return example(summary, 400, detail, Map.of("errors", List.of(Map.of("field", field, "message", message))));
	}

	private static Example example(String summary, Map<String, Object> value) {
		return new Example().summary(summary).value(value);
	}

	private static Map<String, Object> problem(int status, String detail) {
		return problem(status, detail, Map.of());
	}

	private static Map<String, Object> problem(int status, String detail, Map<String, ?> extensions) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("type", "about:blank");
		value.put("title", switch (status) {
			case 400 -> "Bad Request";
			case 401 -> "Unauthorized";
			case 403 -> "Forbidden";
			case 404 -> "Not Found";
			case 409 -> "Conflict";
			default -> "Error";
		});
		value.put("status", status);
		if (detail != null) {
			value.put("detail", detail);
		}
		value.putAll(extensions);
		return value;
	}

	private static void documentErrorResponses(OpenAPI openApi) {
		ERROR_RESPONSES.forEach((route, statuses) -> {
			int separator = route.indexOf(' ');
			String method = route.substring(0, separator);
			String path = route.substring(separator + 1);
			if (openApi.getPaths() == null || openApi.getPaths().get(path) == null) {
				return;
			}
			var pathItem = openApi.getPaths().get(path);
			var operation = switch (method) {
				case "GET" -> pathItem.getGet();
				case "POST" -> pathItem.getPost();
				case "PUT" -> pathItem.getPut();
				case "DELETE" -> pathItem.getDelete();
				default -> null;
			};
			if (operation == null) {
				return;
			}
			statuses.forEach(status -> {
				if ("400".equals(status) && "POST /api/auth/login".equals(route)) {
					operation.getResponses().putIfAbsent(status, new ApiResponse().$ref("#/components/responses/LoginBadRequest"));
				} else {
					String response = switch (status) {
						case "400" -> "BadRequest";
						case "401" -> "Unauthorized";
						case "403" -> "Forbidden";
						case "404" -> "NotFound";
						case "409" -> "Conflict";
						default -> throw new IllegalArgumentException("Unsupported documented status " + status);
					};
					operation.getResponses().putIfAbsent(status,
						new ApiResponse().$ref("#/components/responses/" + response));
				}
			});
		});
	}

	private static void hideStorageLimits(OpenAPI openApi) {
		Map<String, Set<String>> storageLimits = Map.of(
			"RegisterRequest", Set.of("email", "password", "fullName"),
			"CinemaRequest", Set.of("name"),
			"AuditoriumRequest", Set.of("name"),
			"MovieRequest", Set.of("title", "genre"));
		Map<String, Schema> schemas = openApi.getComponents().getSchemas();
		if (schemas == null) {
			return;
		}
		storageLimits.forEach((schemaName, fields) -> {
			Schema<?> schema = schemas.get(schemaName);
			if (schema == null || schema.getProperties() == null) {
				return;
			}
			fields.forEach(field -> {
				Object property = schema.getProperties().get(field);
				if (property instanceof Schema<?> propertySchema) {
					propertySchema.setMaxLength(null);
				}
			});
		});
	}
}
