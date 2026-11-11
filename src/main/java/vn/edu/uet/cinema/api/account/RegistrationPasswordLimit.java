package vn.edu.uet.cinema.api.account;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = RegistrationPasswordLimit.Validator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE,
	ElementType.CONSTRUCTOR, ElementType.TYPE_USE, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface RegistrationPasswordLimit {

	String message() default "Password is not allowed";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	final class Validator implements ConstraintValidator<RegistrationPasswordLimit, String> {

		private static final int MAX_UTF8_BYTES = 72;

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			return value == null || value.length() <= MAX_UTF8_BYTES
				&& value.getBytes(StandardCharsets.UTF_8).length <= MAX_UTF8_BYTES;
		}
	}
}
