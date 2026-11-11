package vn.edu.uet.cinema.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

	private LocalValidatorFactoryBean validator;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new ValidationController())
			.setControllerAdvice(new ApiExceptionHandler())
			.setValidator(validator)
			.build();
	}

	@AfterEach
	void tearDown() {
		validator.close();
	}

	@Test
	void blankLoginGetsGenericProblemWithoutErrorsWithNormalMockMvcRequest() throws Exception {
		MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"person@example.com\",\"password\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Invalid request."))
			.andExpect(jsonPath("$.errors").doesNotExist())
			.andReturn();

		assertThat(result.getRequest().getServletPath()).isEmpty();
	}

	@Test
	void malformedLoginGetsGenericProblemWithoutEchoingPassword() throws Exception {
		String password = "LOGIN_PARSE_CANARY";
		String problem = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"person@example.com\",\"password\":\"" + password + "\",}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Invalid request."))
			.andExpect(jsonPath("$.errors").doesNotExist())
			.andReturn().getResponse().getContentAsString();

		assertThat(problem).doesNotContain(password);
	}

	@Test
	void loginPrivacyUsesMatchedRouteWhenApplicationHasContextPath() throws Exception {
		mvc.perform(post("/cinema/api/auth/login").contextPath("/cinema").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"person@example.com\",\"password\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Invalid request."))
			.andExpect(jsonPath("$.errors").doesNotExist());
	}

	@Test
	void registrationValidationStillIncludesSafeFieldErrors() throws Exception {
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"not-an-email\",\"password\":\"password123\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Request validation failed."))
			.andExpect(jsonPath("$.errors[0].field").value("email"))
			.andExpect(jsonPath("$.errors[0].message").value("Email must be valid"));
	}

	@RestController
	static class ValidationController {

		@PostMapping("/api/auth/login")
		void login(@Valid @RequestBody LoginRequest request) {
		}

		@PostMapping("/api/auth/register")
		void register(@Valid @RequestBody RegisterRequest request) {
		}

		record LoginRequest(@NotBlank(message = "Email is required") String email,
			@NotBlank(message = "Password is required") String password) {
		}

		record RegisterRequest(@NotBlank(message = "Email is required") @Email(message = "Email must be valid") String email,
			@NotBlank(message = "Password is required") String password) {
		}
	}
}
