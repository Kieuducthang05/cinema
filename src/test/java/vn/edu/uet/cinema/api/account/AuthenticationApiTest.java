package vn.edu.uet.cinema.api.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.edu.uet.cinema.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthenticationApiTest {

	@Autowired
	MockMvc mvc;

	@Test
	void customerRegistersLogsInAndReadsOwnAccount() throws Exception {
		register("bob@example.com").andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("CUSTOMER"));
		register("BOB@example.com").andExpect(status().isConflict());

		login("bob@example.com", "wrong-password").andExpect(status().isUnauthorized());
		String token = token("bob@example.com", "password123");

		mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("bob@example.com"));
	}

	@Test
	void protectedEndpointsRequireAValidToken() throws Exception {
		mvc.perform(get("/api/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().exists("WWW-Authenticate"))
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		mvc.perform(get("/api/me").header("Authorization", "Bearer not-a-jwt"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().exists("WWW-Authenticate"))
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void catalogueWritesAreAdminOnly() throws Exception {
		register("carol@example.com");
		String customer = token("carol@example.com", "password123");
		String admin = token("admin@cinema.local", "admin12345");

		mvc.perform(post("/api/cinemas").header("Authorization", "Bearer " + customer))
			.andExpect(status().isForbidden())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		mvc.perform(post("/api/cinemas").header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cinema Alpha\",\"address\":\"1 Alpha Street\"}"))
			.andExpect(status().isCreated());
	}

	@Test
	void metricsAreAdminOnly() throws Exception {
		register("dave@example.com");
		String customer = token("dave@example.com", "password123");
		String admin = token("admin@cinema.local", "admin12345");

		mvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + customer))
			.andExpect(status().isForbidden());
		mvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
	}

	@Test
	void apiDocsArePublic() throws Exception {
		mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/auth/login']").exists())
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
	}

	@Test
	void apiDocsLockOnlyProtectedOperations() throws Exception {
		mvc.perform(get("/v3/api-docs"))
			.andExpect(jsonPath("$.security").doesNotExist())
			.andExpect(jsonPath("$.paths['/api/auth/register'].post.security").doesNotExist())
			.andExpect(jsonPath("$.paths['/api/auth/login'].post.security").doesNotExist())
			.andExpect(jsonPath("$.paths['/api/me'].get.security[0].bearerAuth").exists());
	}

	@Test
	void invalidRegistrationListsEveryFieldWithSafeMessages() throws Exception {
		String password = "canary";
		String problem = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"not-an-email\",\"password\":\"" + password + "\",\"fullName\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Request validation failed."))
			.andReturn().getResponse().getContentAsString();

		List<String> fields = JsonPath.read(problem, "$.errors[*].field");
		assertThat(fields).containsExactlyInAnyOrder("email", "password", "fullName");
		assertThat(problem).contains("Password must be at least 8 characters")
			.doesNotContain(password, "72", "255");
	}

	@Test
	void overlongAndUnicodePasswordsHaveSafeValidationMessages() throws Exception {
		String tooLong = "p".repeat(73);
		String longProblem = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"long-" + java.util.UUID.randomUUID()
					+ "@example.com\",\"password\":\"" + tooLong + "\",\"fullName\":\"Long\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andReturn().getResponse().getContentAsString();
		assertThat(longProblem).contains("Password is not allowed").doesNotContain(tooLong, "72");

		String longName = "N".repeat(256);
		String nameProblem = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"name-" + java.util.UUID.randomUUID()
					+ "@example.com\",\"password\":\"password123\",\"fullName\":\"" + longName + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andReturn().getResponse().getContentAsString();
		assertThat(nameProblem).contains("Full name is not allowed").doesNotContain(longName, "255");

		String unicodeShort = "界".repeat(7);
		String unicodeProblem = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"unicode-" + java.util.UUID.randomUUID()
					+ "@example.com\",\"password\":\"" + unicodeShort + "\",\"fullName\":\"Unicode\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andReturn().getResponse().getContentAsString();
		assertThat(unicodeProblem).contains("Password must be at least 8 characters")
			.doesNotContain(unicodeShort, "72");
	}

	@Test
	void loginFailuresStayGenericAndDoNotApplyRegistrationLengthRule() throws Exception {
		String email = "login-" + java.util.UUID.randomUUID() + "@example.com";
		register(email).andExpect(status().isCreated());
		String wrongPassword = login(email, "wrong-password").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid email or password"))
			.andExpect(jsonPath("$.errors").doesNotExist())
			.andReturn().getResponse().getContentAsString();
		String unknownEmail = login("unknown-" + java.util.UUID.randomUUID() + "@example.com", "wrong-password")
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errors").doesNotExist())
			.andReturn().getResponse().getContentAsString();
		String shortPassword = login(email, "short").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.errors").doesNotExist()).andReturn().getResponse().getContentAsString();
		assertThat(unknownEmail).isEqualTo(wrongPassword);
		assertThat(shortPassword).isEqualTo(wrongPassword);

		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Invalid request."))
			.andExpect(jsonPath("$.errors").doesNotExist());

		String sensitive = "LOGIN_PARSE_CANARY";
		String malformed = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + sensitive + "\",}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Invalid request."))
			.andExpect(jsonPath("$.errors").doesNotExist())
			.andReturn().getResponse().getContentAsString();
		assertThat(malformed).doesNotContain(sensitive);
	}

	@Test
	void unreadableRegistrationBodyDoesNotEchoPassword() throws Exception {
		String password = "SENSITIVE_PASSWORD_CANARY";
		String problem = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"person@example.com\",\"password\":\"" + password + "\",\"fullName\":\"A\",}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Request body could not be read."))
			.andReturn().getResponse().getContentAsString();
		assertThat(problem).doesNotContain(password);
	}

	@Test
	void apiDocsDescribeValidationErrorsAndHideStorageLimits() throws Exception {
		mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/auth/register'].post.responses['400'].content['application/problem+json'].schema.$ref")
				.value("#/components/schemas/ValidationProblem"))
			.andExpect(jsonPath("$.paths['/api/auth/register'].post.responses['400'].content['application/problem+json'].examples")
				.exists())
			.andExpect(jsonPath("$.components.schemas.ValidationProblem.properties.errors.type").value("array"))
			.andExpect(jsonPath("$.components.schemas.ValidationProblem.properties.errors.items.properties.field.type")
				.value("string"))
			.andExpect(jsonPath("$.components.schemas.ValidationProblem.properties.errors.items.properties.message.type")
				.value("string"))
			.andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.password.minLength").value(8))
			.andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.password.maxLength").doesNotExist())
			.andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.email.maxLength").doesNotExist())
			.andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.fullName.maxLength").doesNotExist())
			.andExpect(jsonPath("$.components.schemas.CinemaRequest.properties.name.maxLength").doesNotExist())
			.andExpect(jsonPath("$.components.schemas.AuditoriumRequest.properties.name.maxLength").doesNotExist())
			.andExpect(jsonPath("$.components.schemas.MovieRequest.properties.title.maxLength").doesNotExist())
			.andExpect(jsonPath("$.components.schemas.MovieRequest.properties.genre.maxLength").doesNotExist())
			.andExpect(jsonPath("$.components.schemas.AuditoriumRequest.properties.rows.maximum").value(26))
			.andExpect(jsonPath("$.components.schemas.BookingRequest.properties.seatIds.minItems").value(1))
			.andExpect(jsonPath("$.components.schemas.BookingRequest.properties.seatIds.maxItems").value(8))
			.andExpect(jsonPath("$.components.schemas.CancellationRequest.properties.note.maxLength").value(200));
	}

	private ResultActions register(String email) throws Exception {
		return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"Test\"}".formatted(email)));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}

	private String token(String email, String password) throws Exception {
		String body = login(email, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}
}
