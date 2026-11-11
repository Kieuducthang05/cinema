package vn.edu.uet.cinema.api.auditorium;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.uet.cinema.business.auditorium.Auditorium;
import vn.edu.uet.cinema.business.auditorium.AuditoriumService;
import vn.edu.uet.cinema.business.auditorium.Seat;
import vn.edu.uet.cinema.config.OpenApiConfig;

@RestController
@Tag(name = "Auditoriums", description = "Cinema auditoriums, seat layouts, and public seat reads.")
class AuditoriumController {

	private final AuditoriumService auditoriums;

	AuditoriumController(AuditoriumService auditoriums) {
		this.auditoriums = auditoriums;
	}

	@PostMapping("/api/cinemas/{id}/auditoriums")
	@Operation(summary = "Create an auditorium", description = "Creates an auditorium under an existing cinema. Its name must "
		+ "be unique within that cinema. Rows are A–Z (1–26); each row has a positive seat count. VIP and couple rows must "
		+ "refer to distinct valid row letters. Missing cinema returns 404 and a duplicate name returns 409. Requires ADMIN.")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	AuditoriumResponse create(
		@Parameter(description = "Owning cinema UUID.", example = "550e8400-e29b-41d4-a716-446655440001") @PathVariable UUID id,
		@Valid @RequestBody AuditoriumRequest request) {
		return AuditoriumResponse.detail(auditoriums.create(id, request.name(), request.rows(), request.seatsPerRow(),
			request.vipRows(), request.coupleRows()));
	}

	@GetMapping("/api/cinemas/{id}/auditoriums")
	@Operation(summary = "List a cinema's auditoriums", description = "Returns auditorium summaries for an existing cinema; "
		+ "seat details are omitted. Missing cinema returns 404. This catalogue read is public.")
	List<AuditoriumResponse> list(
		@Parameter(description = "Cinema UUID.", example = "550e8400-e29b-41d4-a716-446655440001") @PathVariable UUID id) {
		return auditoriums.list(id).stream().map(AuditoriumResponse::summary).toList();
	}

	@GetMapping("/api/auditoriums/{id}")
	@Operation(summary = "Get an auditorium", description = "Returns an auditorium and its generated seats. This catalogue read "
		+ "is public; an unknown auditorium returns 404.")
	AuditoriumResponse get(
		@Parameter(description = "Auditorium UUID.", example = "550e8400-e29b-41d4-a716-446655440003") @PathVariable UUID id) {
		return AuditoriumResponse.detail(auditoriums.get(id));
	}

	@DeleteMapping("/api/auditoriums/{id}")
	@Operation(summary = "Delete an auditorium", description = "Hard-deletes an auditorium only when it has no showtimes. "
		+ "Requires ADMIN; an unknown auditorium returns 404 and dependent showtimes cause 409.")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	void delete(
		@Parameter(description = "Auditorium UUID.", example = "550e8400-e29b-41d4-a716-446655440003") @PathVariable UUID id) {
		auditoriums.delete(id);
	}

	@Schema(name = "AuditoriumRequest")
		record AuditoriumRequest(@NotBlank(message = "Name is required")
		@Size(max = 255, message = "Name is not allowed")
		@Schema(description = "Nonblank name unique within its cinema.", minLength = 1, example = "Screen 1") String name,
		@NotNull(message = "Rows are required") @Min(value = 1, message = "Rows must be at least 1")
		@Max(value = 26, message = "Rows must be at most 26")
		@Schema(description = "Lettered rows A–Z; choose 1 to 26 rows.", minimum = "1", maximum = "26", example = "12")
		Integer rows,
		@NotNull(message = "Seats per row are required") @Positive(message = "Seats per row must be positive")
		@Schema(description = "Positive number of seats in each row.", minimum = "1", example = "10")
		Integer seatsPerRow,
		@NotNull(message = "VIP rows are required")
		@ArraySchema(arraySchema = @Schema(description = "Rows designated as VIP.", example = "[\"B\"]"),
			schema = @Schema(example = "B")) List<String> vipRows,
		@NotNull(message = "Couple rows are required")
		@ArraySchema(arraySchema = @Schema(description = "Rows designated as Couple.", example = "[\"C\"]"),
			schema = @Schema(example = "C")) List<String> coupleRows) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record AuditoriumResponse(
		@Schema(description = "Auditorium identifier.", example = "550e8400-e29b-41d4-a716-446655440000") UUID id,
		@Schema(description = "Owning cinema identifier.", example = "550e8400-e29b-41d4-a716-446655440001") UUID cinemaId,
		@Schema(description = "Auditorium name.", example = "Screen 1") String name,
		@Schema(description = "Number of lettered rows.", example = "12") int rows,
		@Schema(description = "Seats per row.", example = "10") int seatsPerRow,
		@ArraySchema(arraySchema = @Schema(description = "Generated seats; omitted in auditorium summaries."),
			schema = @Schema(implementation = SeatResponse.class)) List<SeatResponse> seats) {

		static AuditoriumResponse summary(Auditorium auditorium) {
			return new AuditoriumResponse(auditorium.id(), auditorium.cinemaId(), auditorium.name(), auditorium.rows(),
				auditorium.seatsPerRow(), null);
		}

		static AuditoriumResponse detail(Auditorium auditorium) {
			return new AuditoriumResponse(auditorium.id(), auditorium.cinemaId(), auditorium.name(), auditorium.rows(),
				auditorium.seatsPerRow(), auditorium.seats().stream().map(SeatResponse::from).toList());
		}
	}

	record SeatResponse(
		@Schema(description = "Seat identifier.", example = "550e8400-e29b-41d4-a716-446655440002") UUID id,
		@Schema(description = "Lettered row.", example = "A") String row,
		@Schema(description = "One-based seat position in the row.", example = "1") int number,
		@Schema(description = "Seat label.", example = "A1") String label,
		@Schema(description = "Seat type.", example = "STANDARD") String type) {

		static SeatResponse from(Seat seat) {
			return new SeatResponse(seat.id(), seat.row(), seat.number(), seat.label(), seat.type().name());
		}
	}
}
