package vn.edu.uet.cinema.api.account;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.uet.cinema.business.account.Account;
import vn.edu.uet.cinema.business.account.AccountService;
import vn.edu.uet.cinema.config.OpenApiConfig;

@RestController
@Tag(name = "Accounts", description = "Customer registration, login, and the authenticated account.")
class AccountController {

	private final AccountService accounts;
	private final TokenIssuer tokens;

	AccountController(AccountService accounts, TokenIssuer tokens) {
		this.accounts = accounts;
		this.tokens = tokens;
	}

	@PostMapping("/api/auth/register")
	@Operation(summary = "Register a customer account", description = "Public self-registration creates a CUSTOMER account. "
		+ "Email addresses are normalized; an existing email returns 409. The password must contain at least 8 characters.")
	@ResponseStatus(HttpStatus.CREATED)
	@ApiResponse(responseCode = "400", description = "The request contains invalid fields.",
		content = @Content(mediaType = "application/problem+json",
			schema = @Schema(ref = "#/components/schemas/ValidationProblem"),
			examples = @ExampleObject(name = "Invalid registration fields", value = "{\"type\":\"about:blank\","
				+ "\"title\":\"Bad Request\",\"status\":400,\"detail\":\"Request validation failed.\","
				+ "\"errors\":[{\"field\":\"password\","
				+ "\"message\":\"Password must be at least 8 characters\"}]}")))
	AccountResponse register(@Valid @RequestBody RegisterRequest request) {
		return AccountResponse.from(accounts.registerCustomer(request.email(), request.password(), request.fullName()));
	}

	@PostMapping("/api/auth/login")
	@Operation(summary = "Log in", description = "Public login authenticates with email and password and returns a one-hour "
		+ "Bearer token. Unknown email and wrong password share the same 401 response; login applies only nonblank validation.")
	TokenResponse login(@Valid @RequestBody LoginRequest request) {
		Account account = accounts.authenticate(request.email(), request.password());
		return new TokenResponse(tokens.issue(account), "Bearer", TokenIssuer.TTL.toSeconds());
	}

	@GetMapping("/api/me")
	@Operation(summary = "Get the authenticated account", description = "Returns the account identified by the JWT subject. "
		+ "Any authenticated role may call this operation; if the account no longer exists the response is 404.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	AccountResponse me(@AuthenticationPrincipal Jwt jwt) {
		return AccountResponse.from(accounts.get(UUID.fromString(jwt.getSubject())));
	}

	// BCrypt only reads the first 72 bytes of a password.
	@Schema(name = "RegisterRequest")
	record RegisterRequest(@NotBlank(message = "Email is required") @Email(message = "Email must be valid")
		@Size(max = 255, message = "Email is not allowed")
		@Schema(description = "Nonblank email address used to identify the account.", minLength = 1,
			example = "alice@example.com") String email,
		@NotBlank(message = "Password is required")
		@Size(min = 8, message = "Password must be at least 8 characters") @RegistrationPasswordLimit
		@Schema(description = "Password with a minimum length of 8 characters.", minLength = 8,
			example = "correctHorseBatteryStaple") String password,
		@NotBlank(message = "Full name is required") @Size(max = 255, message = "Full name is not allowed")
		@Schema(description = "Customer's nonblank full name.", minLength = 1, example = "Alice Nguyen") String fullName) {
	}

	record LoginRequest(@NotBlank @Schema(description = "Registered email address.", minLength = 1,
		example = "alice@example.com") String email,
		@NotBlank @Schema(description = "Account password.", minLength = 1,
			example = "correctHorseBatteryStaple") String password) {
	}

	record TokenResponse(@Schema(description = "Signed HS256 access token.") String accessToken,
		@Schema(description = "Authorization scheme.", example = "Bearer") String tokenType,
		@Schema(description = "Token lifetime in seconds.", example = "3600") long expiresIn) {
	}

	record AccountResponse(@Schema(description = "Account identifier.", example = "550e8400-e29b-41d4-a716-446655440000")
		UUID id,
		@Schema(description = "Normalized email address.", example = "alice@example.com") String email,
		@Schema(description = "Account holder's full name.", example = "Alice Nguyen") String fullName,
		@Schema(description = "Account role.", example = "CUSTOMER") String role) {

		static AccountResponse from(Account account) {
			return new AccountResponse(account.id(), account.email(), account.fullName(), account.role().name());
		}
	}
}
