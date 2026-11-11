package vn.edu.uet.cinema.api.showtime;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.uet.cinema.business.auditorium.Seat;
import vn.edu.uet.cinema.business.showtime.Cancellation;
import vn.edu.uet.cinema.business.showtime.CancellationReason;
import vn.edu.uet.cinema.business.showtime.SeatStatus;
import vn.edu.uet.cinema.business.showtime.Showtime;
import vn.edu.uet.cinema.business.showtime.ShowtimeService;
import vn.edu.uet.cinema.business.showtime.ShowtimeStatus;
import vn.edu.uet.cinema.config.OpenApiConfig;

@RestController
@Tag(name = "Showtimes", description = "Scheduled screenings, cancellation, and public seat availability.")
class ShowtimeController {

	private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	private final ShowtimeService showtimes;

	ShowtimeController(ShowtimeService showtimes) {
		this.showtimes = showtimes;
	}

	@PostMapping("/api/showtimes")
	@Operation(summary = "Create a showtime", description = "Schedules a movie in an auditorium. End time is fixed at creation "
		+ "as movie duration plus 15 minutes for cleaning; screenings in the same auditorium cannot overlap. Base price is "
		+ "a positive VND amount. Missing movie/auditorium returns 404 and an overlap returns 409. Requires ADMIN.")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	ShowtimeResponse create(@Valid @RequestBody ShowtimeRequest request) {
		return ShowtimeResponse.from(showtimes.create(request.movieId(), request.auditoriumId(),
			request.startsAt().toInstant(), request.basePrice()));
	}

	@GetMapping("/api/showtimes")
	@Operation(summary = "List showtimes for a date", description = "Returns non-cancelled showtimes starting on the given "
		+ "Vietnam local calendar date. The date is required; cinemaId and movieId optionally filter the results. This read is public.")
	List<ShowtimeResponse> list(
		@Parameter(description = "Required Vietnam local date, interpreted from 00:00 to 24:00.", example = "2026-05-01")
		@RequestParam LocalDate date,
		@Parameter(description = "Optional cinema UUID filter.", example = "550e8400-e29b-41d4-a716-446655440001")
		@RequestParam(required = false) UUID cinemaId,
		@Parameter(description = "Optional movie UUID filter.", example = "550e8400-e29b-41d4-a716-446655440002")
		@RequestParam(required = false) UUID movieId) {
		Instant from = date.atStartOfDay(VIETNAM_ZONE).toInstant();
		Instant to = date.plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant();
		return showtimes.list(from, to, cinemaId, movieId).stream().map(ShowtimeResponse::from).toList();
	}

	@GetMapping("/api/showtimes/{id}")
	@Operation(summary = "Get a showtime", description = "Returns a showtime by UUID, including its cancellation status and "
		+ "reason when cancelled. An unknown showtime returns 404. This read is public.")
	ShowtimeResponse get(@Parameter(description = "Showtime UUID.", example = "550e8400-e29b-41d4-a716-446655440010")
		@PathVariable UUID id) {
		return ShowtimeResponse.from(showtimes.get(id));
	}

	@GetMapping("/api/showtimes/{id}/seats")
	@Operation(summary = "Get a showtime's seat map", description = "Returns every seat with its current availability and "
		+ "calculated VND price. An unknown showtime returns 404; a cancelled showtime returns 409. This read is public.")
	List<SeatMapResponse> seats(
		@Parameter(description = "Showtime UUID.", example = "550e8400-e29b-41d4-a716-446655440010") @PathVariable UUID id) {
		return showtimes.seatMap(id, Instant.now()).stream()
			.map(entry -> SeatMapResponse.from(entry.seat().seat(), entry.seat().price(), entry.status())).toList();
	}

	@PostMapping("/api/showtimes/{id}/cancellation")
	@Operation(summary = "Cancel a showtime", description = "Cancels a showtime until its endsAt. A reason is required; OTHER "
		+ "requires a nonblank note of at most 200 characters. Confirmed bookings are refunded and Pending bookings removed. "
		+ "An unknown showtime returns 404; a cancelled or ended showtime returns 409. Requires ADMIN.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	ShowtimeResponse cancel(
		@Parameter(description = "Showtime UUID.", example = "550e8400-e29b-41d4-a716-446655440010") @PathVariable UUID id,
		@Valid @RequestBody CancellationRequest request) {
		return ShowtimeResponse.from(showtimes.cancel(id, request.reason(), request.note(), Instant.now()));
	}

	@DeleteMapping("/api/showtimes/{id}")
	@Operation(summary = "Delete a showtime", description = "Hard-deletes a showtime only when it has no live Pending or "
		+ "Confirmed/Refunded bookings; lapsed holds are removed with it. Unknown showtime returns 404, dependent booking 409. "
		+ "Requires ADMIN.")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	void delete(@Parameter(description = "Showtime UUID.", example = "550e8400-e29b-41d4-a716-446655440010")
		@PathVariable UUID id) {
		showtimes.delete(id, Instant.now());
	}

	record ShowtimeRequest(@NotNull(message = "Movie is required")
		@Schema(description = "Movie to screen.", example = "550e8400-e29b-41d4-a716-446655440000") UUID movieId,
		@NotNull(message = "Auditorium is required")
		@Schema(description = "Auditorium for the screening.", example = "550e8400-e29b-41d4-a716-446655440001") UUID auditoriumId,
		@NotNull(message = "Start time is required")
		@Schema(description = "Start instant as ISO-8601 date-time with an offset.", example = "2026-05-01T19:30:00+07:00")
		OffsetDateTime startsAt,
		@NotNull(message = "Base price is required") @Positive(message = "Base price must be positive")
		@Schema(description = "Positive integer VND base price.", minimum = "1", example = "100000") Long basePrice) {
	}

	record CancellationRequest(@NotNull(message = "Cancellation reason is required")
		@Schema(description = "Cancellation reason. OTHER requires a nonblank note.", example = "EQUIPMENT_FAILURE")
		CancellationReason reason,
		@Schema(description = "Optional note (0–200 characters); required when reason is OTHER.", minLength = 0,
			maxLength = 200, example = "Projector failure") String note) {
	}

	/** {@code cancellation} is null unless the Showtime is Cancelled. */
	record ShowtimeResponse(
		@Schema(description = "Showtime identifier.", example = "550e8400-e29b-41d4-a716-446655440010") UUID id,
		@Schema(description = "Scheduled movie identifier.", example = "550e8400-e29b-41d4-a716-446655440000") UUID movieId,
		@Schema(description = "Scheduled auditorium identifier.", example = "550e8400-e29b-41d4-a716-446655440001") UUID auditoriumId,
		@Schema(description = "Start instant.", example = "2026-05-01T19:30:00Z") Instant startsAt,
		@Schema(description = "Fixed end instant (movie duration plus 15 minutes).", example = "2026-05-01T21:45:00Z")
		Instant endsAt,
		@Schema(description = "Base price in integer VND.", example = "100000") long basePrice,
		@Schema(description = "Current status.", example = "SCHEDULED") ShowtimeStatus status,
		@Schema(description = "Cancellation details; null unless cancelled.") CancellationResponse cancellation) {

		static ShowtimeResponse from(Showtime showtime) {
			Cancellation cancellation = showtime.cancellation();
			return new ShowtimeResponse(showtime.id(), showtime.movieId(), showtime.auditoriumId(), showtime.startsAt(),
				showtime.endsAt(), showtime.basePrice(), showtime.status(),
				cancellation == null ? null : new CancellationResponse(cancellation.reason(), cancellation.note()));
		}
	}

	record CancellationResponse(
		@Schema(description = "Cancellation reason.", example = "EQUIPMENT_FAILURE") CancellationReason reason,
		@Schema(description = "Cancellation note, if supplied.", example = "Projector failure") String note) {
	}

	record SeatMapResponse(
		@Schema(description = "Seat identifier.", example = "550e8400-e29b-41d4-a716-446655440020") UUID id,
		@Schema(description = "Lettered seat row.", example = "A") String row,
		@Schema(description = "One-based seat position in the row.", example = "1") int number,
		@Schema(description = "Seat label.", example = "A1") String label,
		@Schema(description = "Seat type.", example = "STANDARD") String type,
		@Schema(description = "Price for this seat in integer VND.", example = "100000") long price,
		@Schema(description = "Availability status.", example = "AVAILABLE") SeatStatus status) {

		static SeatMapResponse from(Seat seat, long price, SeatStatus status) {
			return new SeatMapResponse(seat.id(), seat.row(), seat.number(), seat.label(), seat.type().name(), price, status);
		}
	}
}
