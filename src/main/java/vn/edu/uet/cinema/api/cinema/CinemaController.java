package vn.edu.uet.cinema.api.cinema;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import vn.edu.uet.cinema.business.cinema.Cinema;
import vn.edu.uet.cinema.business.cinema.CinemaService;
import vn.edu.uet.cinema.config.OpenApiConfig;

@RestController
@Tag(name = "Cinemas", description = "Cinema catalogue management and public cinema reads.")
class CinemaController {

	private final CinemaService cinemas;

	CinemaController(CinemaService cinemas) {
		this.cinemas = cinemas;
	}

	@PostMapping("/api/cinemas")
	@Operation(summary = "Create a cinema", description = "Creates a cinema. The name and address must be nonblank; "
		+ "an authenticated ADMIN role is required.")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	CinemaResponse create(@Valid @RequestBody CinemaRequest request) {
		return CinemaResponse.from(cinemas.create(request.name(), request.address()));
	}

	@GetMapping("/api/cinemas")
	@Operation(summary = "List cinemas", description = "Returns all cinemas. This catalogue read is public; a supplied "
		+ "invalid bearer token is rejected.")
	List<CinemaResponse> list() {
		return cinemas.list().stream().map(CinemaResponse::from).toList();
	}

	@GetMapping("/api/cinemas/{id}")
	@Operation(summary = "Get a cinema", description = "Returns one cinema by UUID. The read is public; an unknown cinema "
		+ "returns 404.")
	CinemaResponse get(@Parameter(description = "Cinema UUID.", example = "550e8400-e29b-41d4-a716-446655440001")
		@PathVariable UUID id) {
		return CinemaResponse.from(cinemas.get(id));
	}

	@PutMapping("/api/cinemas/{id}")
	@Operation(summary = "Update a cinema", description = "Replaces a cinema's name and address. The cinema must exist; "
		+ "a missing cinema returns 404. Requires ADMIN.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	CinemaResponse update(
		@Parameter(description = "Cinema UUID.", example = "550e8400-e29b-41d4-a716-446655440001") @PathVariable UUID id,
		@Valid @RequestBody CinemaRequest request) {
		return CinemaResponse.from(cinemas.update(id, request.name(), request.address()));
	}

	@DeleteMapping("/api/cinemas/{id}")
	@Operation(summary = "Delete a cinema", description = "Hard-deletes a cinema that has no auditoriums. An authenticated "
		+ "ADMIN role is required; a missing cinema returns 404 and existing auditoriums cause 409.")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	void delete(@Parameter(description = "Cinema UUID.", example = "550e8400-e29b-41d4-a716-446655440001")
		@PathVariable UUID id) {
		cinemas.delete(id);
	}

	@Schema(name = "CinemaRequest")
	record CinemaRequest(@NotBlank(message = "Name is required")
		@Size(max = 255, message = "Name is not allowed")
		@Schema(description = "Nonblank cinema name.", minLength = 1, example = "Central Cinema") String name,
		@NotBlank(message = "Address is required")
		@Schema(description = "Nonblank street address; no API length limit is defined.", minLength = 1,
			example = "1 Example Street") String address) {
	}

	record CinemaResponse(@Schema(description = "Cinema identifier.", example = "550e8400-e29b-41d4-a716-446655440000")
		UUID id,
		@Schema(description = "Cinema name.", example = "Central Cinema") String name,
		@Schema(description = "Street address.", example = "1 Example Street") String address) {

		static CinemaResponse from(Cinema cinema) {
			return new CinemaResponse(cinema.id(), cinema.name(), cinema.address());
		}
	}
}
