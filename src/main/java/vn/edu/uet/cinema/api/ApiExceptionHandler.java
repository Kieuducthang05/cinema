package vn.edu.uet.cinema.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;
import vn.edu.uet.cinema.business.ConflictException;
import vn.edu.uet.cinema.business.NotFoundException;
import vn.edu.uet.cinema.business.account.InvalidCredentialsException;
import vn.edu.uet.cinema.business.auditorium.InvalidSeatLayoutException;
import vn.edu.uet.cinema.business.booking.InvalidBookingException;
import vn.edu.uet.cinema.business.booking.SeatsTakenException;
import vn.edu.uet.cinema.business.showtime.InvalidShowtimeException;

/**
 * Maps business and framework exceptions to RFC 9457 problem details.
 */
@RestControllerAdvice
@Order(-1)
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String VALIDATION_DETAIL = "Request validation failed.";
	private static final String LOGIN_VALIDATION_DETAIL = "Invalid request.";
	private static final String UNREADABLE_DETAIL = "Request body could not be read.";

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers,
		HttpStatusCode status, WebRequest request) {
		if (isLogin(request)) {
			return problem(e, headers, status, request, LOGIN_VALIDATION_DETAIL, List.of());
		}
		return problem(e, headers, status, request, VALIDATION_DETAIL, validationErrors(e.getBindingResult()));
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException e,
		HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		if (e.isForReturnValue()) {
			return problem(e, headers, status, request, "Response validation failed.", List.of());
		}
		if (isLogin(request)) {
			return problem(e, headers, status, request, LOGIN_VALIDATION_DETAIL, List.of());
		}
		return problem(e, headers, status, request, VALIDATION_DETAIL, validationErrors(e));
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e, HttpHeaders headers,
		HttpStatusCode status, WebRequest request) {
		if (isLogin(request)) {
			return problem(e, headers, status, request, LOGIN_VALIDATION_DETAIL, List.of());
		}
		InvalidFormatException invalid = invalidFormatCause(e);
		if (invalid == null) {
			return problem(e, headers, status, request, UNREADABLE_DETAIL, List.of());
		}
		return problem(e, headers, status, request, "Request body contains an invalid value.",
			List.of(new ValidationError(fieldName(invalid), invalidFormatMessage(invalid.getTargetType()))));
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException e, HttpHeaders headers, HttpStatusCode status,
		WebRequest request) {
		if (isLogin(request)) {
			return problem(e, headers, status, request, LOGIN_VALIDATION_DETAIL, List.of());
		}
		if (e instanceof MethodArgumentTypeMismatchException mismatch) {
			return problem(e, headers, status, request, "A request value has an invalid type.",
				List.of(new ValidationError(mismatch.getName(), invalidFormatMessage(mismatch.getRequiredType()))));
		}
		return problem(e, headers, status, request, "A request value has an invalid type.", List.of());
	}

	private ResponseEntity<Object> problem(Exception exception, HttpHeaders headers, HttpStatusCode status,
		WebRequest request, String detail, List<ValidationError> errors) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		if (!errors.isEmpty()) {
			problem.setProperty("errors", errors);
		}
		return super.handleExceptionInternal(exception, problem, headers, status, request);
	}

	private static List<ValidationError> validationErrors(Errors result) {
		List<ValidationError> errors = new ArrayList<>();
		for (FieldError error : result.getFieldErrors()) {
			errors.add(new ValidationError(error.getField(), safeMessage(error)));
		}
		for (ObjectError error : result.getGlobalErrors()) {
			errors.add(new ValidationError("body", safeMessage(error)));
		}
		return List.copyOf(errors);
	}

	private static List<ValidationError> validationErrors(HandlerMethodValidationException exception) {
		List<ValidationError> errors = new ArrayList<>();
		for (ParameterValidationResult result : exception.getParameterValidationResults()) {
			if (result instanceof ParameterErrors parameterErrors) {
				errors.addAll(validationErrors(parameterErrors));
			} else {
				addParameterErrors(errors, result);
			}
		}
		if (!exception.getCrossParameterValidationResults().isEmpty()) {
			errors.add(new ValidationError("request", "Request parameters are invalid."));
		}
		return List.copyOf(errors);
	}

	private static void addParameterErrors(List<ValidationError> errors, ParameterValidationResult result) {
		String field = result.getMethodParameter().getParameterName();
		if (field == null || field.isBlank()) {
			field = "request";
		}
		if (result.getContainerIndex() != null) {
			field += "[" + result.getContainerIndex() + "]";
		}
		for (MessageSourceResolvable error : result.getResolvableErrors()) {
			errors.add(new ValidationError(field, safeMessage(error)));
		}
	}

	private static String safeMessage(MessageSourceResolvable error) {
		String message = error.getDefaultMessage();
		return message == null || message.isBlank() ? "Invalid value." : message;
	}

	private static InvalidFormatException invalidFormatCause(Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof InvalidFormatException invalid) {
				return invalid;
			}
		}
		return null;
	}

	private static String fieldName(InvalidFormatException error) {
		StringBuilder field = new StringBuilder();
		for (JacksonException.Reference reference : error.getPath()) {
			String property = reference.getPropertyName();
			if (property != null && !property.isBlank()) {
				if (!field.isEmpty()) {
					field.append('.');
				}
				field.append(property);
			}
			if (reference.getIndex() >= 0) {
				field.append('[').append(reference.getIndex()).append(']');
			}
		}
		return field.isEmpty() ? "body" : field.toString();
	}

	private static String invalidFormatMessage(Class<?> targetType) {
		if (targetType == null) {
			return "Invalid value.";
		}
		if (targetType.isEnum()) {
			String values = Arrays.stream(targetType.getEnumConstants())
				.map(value -> ((Enum<?>) value).name()).toList().toString();
			return "Allowed values are " + values + ".";
		}
		if (targetType == UUID.class) {
			return "Must be a valid UUID.";
		}
		if (targetType == LocalDate.class) {
			return "Must be an ISO-8601 date (yyyy-MM-dd).";
		}
		if (targetType == OffsetDateTime.class) {
			return "Must be an ISO-8601 date-time with an offset.";
		}
		return "Invalid value.";
	}

	private static boolean isLogin(WebRequest request) {
		return "/api/auth/login".equals(request.getAttribute(
			HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));
	}

	record ValidationError(String field, String message) {
	}

	@ExceptionHandler
	ProblemDetail notFound(NotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler
	ProblemDetail conflict(ConflictException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler
	ProblemDetail seatsTaken(SeatsTakenException e) {
		ProblemDetail problem = conflict(e);
		problem.setProperty("seatIds", e.seatIds());
		return problem;
	}

	@ExceptionHandler
	ProblemDetail invalidSeatLayout(InvalidSeatLayoutException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler
	ProblemDetail invalidShowtime(InvalidShowtimeException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler
	ProblemDetail invalidBooking(InvalidBookingException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler
	ProblemDetail invalidCredentials(InvalidCredentialsException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
	}
}
