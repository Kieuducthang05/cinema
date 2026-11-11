package vn.edu.uet.cinema.api.account;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import vn.edu.uet.cinema.config.OpenApiConfig;

class RegistrationPasswordLimitTest {

	@Test
	void enforcesBcryptUtf8LimitWithoutChangingTheEightCharacterMinimum() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();
			assertThat(violations(validator, "a".repeat(72))).isEmpty();
			assertPasswordError(validator, "a".repeat(73), "Password is not allowed");
			assertThat(violations(validator, "界".repeat(24))).isEmpty();
			assertPasswordError(validator, "界".repeat(25), "Password is not allowed");
			assertPasswordError(validator, "short6", "Password must be at least 8 characters");
			assertPasswordError(validator, "界".repeat(7), "Password must be at least 8 characters");

			Set<ConstraintViolation<AccountController.RegisterRequest>> missing = violations(validator, null);
			assertThat(missing).singleElement().satisfies(error -> {
				assertThat(error.getPropertyPath().toString()).isEqualTo("password");
				assertThat(error.getMessage()).isEqualTo("Password is required");
			});
		}
	}

	@Test
	void openApiCustomizerKeepsPasswordMinimumAndRemovesRawMaximumSentinel() {
		ResolvedSchema resolved = ModelConverters.getInstance()
			.readAllAsResolvedSchema(AccountController.RegisterRequest.class);
		Map<String, Schema> schemas = new HashMap<>(resolved.referencedSchemas);
		schemas.put("RegisterRequest", resolved.schema);
		OpenAPI openApi = new OpenAPI().components(new Components().schemas(schemas));
		Schema<?> rawPassword = (Schema<?>) resolved.schema.getProperties().get("password");
		assertThat(rawPassword.getMinLength()).isEqualTo(8);
		assertThat(rawPassword.getMaxLength()).isEqualTo(Integer.MAX_VALUE);

		try (var context = new AnnotationConfigApplicationContext(OpenApiConfig.class)) {
			OpenApiCustomizer customizer = context.getBean(OpenApiCustomizer.class);
			customizer.customise(openApi);
		}

		Schema<?> customizedPassword = (Schema<?>) openApi.getComponents().getSchemas()
			.get("RegisterRequest").getProperties().get("password");
		assertThat(customizedPassword.getMinLength()).isEqualTo(8);
		assertThat(customizedPassword.getMaxLength()).isNull();
		Schema<?> problem = openApi.getComponents().getSchemas().get("ValidationProblem");
		assertThat(problem.getRequired()).containsExactlyInAnyOrder("title", "status");
		assertThat(((Schema<?>) problem.getProperties().get("type")).getDefault()).isEqualTo("about:blank");
	}

	private static void assertPasswordError(Validator validator, String password, String expectedMessage) {
		Set<ConstraintViolation<AccountController.RegisterRequest>> errors = violations(validator, password);
		assertThat(errors).singleElement().satisfies(error -> {
			assertThat(error.getPropertyPath().toString()).isEqualTo("password");
			assertThat(error.getMessage()).isEqualTo(expectedMessage)
				.doesNotContain("72", password);
		});
	}

	private static Set<ConstraintViolation<AccountController.RegisterRequest>> violations(Validator validator,
		String password) {
		return validator.validate(new AccountController.RegisterRequest("person@example.com", password, "Person"));
	}
}
