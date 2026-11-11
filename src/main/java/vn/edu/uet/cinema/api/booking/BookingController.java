package vn.edu.uet.cinema.api.booking;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.uet.cinema.business.booking.BookedSeat;
import vn.edu.uet.cinema.business.booking.Booking;
import vn.edu.uet.cinema.business.booking.BookingService;
import vn.edu.uet.cinema.business.booking.BookingStatus;
import vn.edu.uet.cinema.config.OpenApiConfig;

@RestController
@Tag(name = "Bookings", description = "Customer-owned seat holds, payment, and booking retrieval.")
class BookingController {

	private final BookingService bookings;

	BookingController(BookingService bookings) {
		this.bookings = bookings;
	}

	@PostMapping("/api/bookings")
	@Operation(summary = "Book seats", description = "Creates a 10-minute Pending hold for 1–8 unique seats in a future, "
		+ "non-cancelled showtime. Customers can have at most one live Pending booking; taken seats, cancelled/started "
		+ "showtimes, or an existing hold cause 409. Duplicate IDs or seats outside its auditorium are 400; an unknown "
		+ "showtime is 404. Requires CUSTOMER.")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	BookingResponse book(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BookingRequest request) {
		Instant now = Instant.now();
		return BookingResponse.from(bookings.book(customerId(jwt), request.showtimeId(), request.seatIds(), now), now);
	}

	@GetMapping("/api/bookings")
	@Operation(summary = "List my bookings", description = "Returns only the authenticated customer's bookings, newest first. "
		+ "Requires CUSTOMER.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	List<BookingResponse> list(@AuthenticationPrincipal Jwt jwt) {
		Instant now = Instant.now();
		return bookings.list(customerId(jwt)).stream().map(booking -> BookingResponse.from(booking, now)).toList();
	}

	@GetMapping("/api/bookings/{id}")
	@Operation(summary = "Get my booking", description = "Returns a booking owned by the authenticated customer. Another "
		+ "customer's booking is indistinguishable from a missing booking and returns 404. Requires CUSTOMER.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	BookingResponse get(@AuthenticationPrincipal Jwt jwt,
		@Parameter(description = "Booking UUID owned by the authenticated customer.",
			example = "550e8400-e29b-41d4-a716-446655440030") @PathVariable UUID id) {
		return BookingResponse.from(bookings.get(customerId(jwt), id), Instant.now());
	}

	@PostMapping("/api/bookings/{id}/payment")
	@Operation(summary = "Pay for my booking", description = "Simulates payment for a live Pending booking. An expired hold "
		+ "returns 409; retrying payment for an already Confirmed booking returns it unchanged. Missing or another customer's "
		+ "booking returns 404. Requires CUSTOMER.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	BookingResponse pay(@AuthenticationPrincipal Jwt jwt,
		@Parameter(description = "Booking UUID owned by the authenticated customer.",
			example = "550e8400-e29b-41d4-a716-446655440030") @PathVariable UUID id) {
		Instant now = Instant.now();
		return BookingResponse.from(bookings.pay(customerId(jwt), id, now), now);
	}

	@DeleteMapping("/api/bookings/{id}")
	@Operation(summary = "Abandon my pending booking", description = "Deletes a Pending booking and releases its seats. "
		+ "Expired holds can be abandoned too. Confirmed or Refunded bookings cannot be cancelled by a customer and return 409. "
		+ "Missing or another customer's "
		+ "booking returns 404. Requires CUSTOMER.")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	void abandon(@AuthenticationPrincipal Jwt jwt,
		@Parameter(description = "Booking UUID owned by the authenticated customer.",
			example = "550e8400-e29b-41d4-a716-446655440030") @PathVariable UUID id) {
		bookings.abandon(customerId(jwt), id);
	}

	private static UUID customerId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	record BookingRequest(@NotNull(message = "Showtime is required")
		@Schema(description = "Showtime to book.", example = "550e8400-e29b-41d4-a716-446655440010") UUID showtimeId,
		@NotNull(message = "Seats are required")
		@ArraySchema(arraySchema = @Schema(description = "Choose 1–8 unique seat IDs for this showtime.",
			example = "[\"550e8400-e29b-41d4-a716-446655440020\"]"),
			schema = @Schema(type = "string", format = "uuid", example = "550e8400-e29b-41d4-a716-446655440020"),
			minItems = 1, maxItems = 8)
		List<@NotNull(message = "Seat ID is required") UUID> seatIds) {
	}

	record BookingResponse(
		@Schema(description = "Booking identifier.", example = "550e8400-e29b-41d4-a716-446655440030") UUID id,
		@Schema(description = "Booked showtime identifier.", example = "550e8400-e29b-41d4-a716-446655440010") UUID showtimeId,
		@Schema(description = "Booking state, including computed EXPIRED for elapsed holds.", example = "PENDING")
		BookingStatus status,
		@Schema(description = "Booking creation instant.", example = "2026-05-01T12:00:00Z") Instant createdAt,
		@Schema(description = "Hold expiry instant (10 minutes after creation).", example = "2026-05-01T12:10:00Z")
		Instant expiresAt,
		@ArraySchema(arraySchema = @Schema(description = "Seats and their prices copied when booked."),
			schema = @Schema(implementation = BookedSeat.class,
				example = "{\"seatId\":\"550e8400-e29b-41d4-a716-446655440020\",\"price\":100000}"))
		List<BookedSeat> seats,
		@Schema(description = "Total price in integer VND.", example = "100000") long totalPrice) {

		static BookingResponse from(Booking booking, Instant now) {
			return new BookingResponse(booking.id(), booking.showtimeId(), booking.statusAt(now), booking.createdAt(),
				booking.expiresAt(), booking.seats(), booking.totalPrice());
		}
	}
}
