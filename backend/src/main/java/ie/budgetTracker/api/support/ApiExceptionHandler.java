package ie.budgetTracker.api.support;

import ie.budgetTracker.application.AppException;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Failures answered as RFC 7807 problem documents (spec §4).
 *
 * The frontend's `lib/http.ts` throws on any non-2xx and reads `title` from
 * the body, so every failure here has to carry one worth showing a person.
 * "Could not save" is not that; "An account called \"Wallet\" already exists"
 * is, and it is the message the domain already wrote.
 */
@RestControllerAdvice
class ApiExceptionHandler {

	/** One field that failed validation, and why. */
	record FieldError(String field, String message) {
	}

	@ExceptionHandler(AppException.class)
	ProblemDetail handleAppException(AppException exception) {
		HttpStatus status = switch (exception.kind()) {
			case NOT_FOUND -> HttpStatus.NOT_FOUND;
			case CONFLICT -> HttpStatus.CONFLICT;
			case UNAUTHORISED -> HttpStatus.UNAUTHORIZED;
			case INVALID -> HttpStatus.BAD_REQUEST;
			case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
		};

		ProblemDetail problem = ProblemDetail.forStatus(status);
		problem.setTitle(exception.getMessage());
		problem.setType(URI.create("about:blank"));
		problem.setDetail("");
		if (exception.field() != null) {
			// The same `errors` shape bean validation produces below, so a form
			// reading it does not have to know which of the two refused it.
			problem.setProperty("errors",
					List.of(new FieldError(exception.field(), exception.getMessage())));
		}
		return problem;
	}

	/**
	 * A 400 carries the field-level list spec §4 asks for.
	 *
	 * A form that is told only "invalid" has to guess which field it was, and
	 * guessing wrong is worse than not saying.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleValidationFailure(MethodArgumentNotValidException exception) {
		List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
				.toList();

		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		problem.setTitle("That entry could not be saved");
		problem.setType(URI.create("about:blank"));
		problem.setDetail("");
		problem.setProperty("errors", errors);
		return problem;
	}
}
