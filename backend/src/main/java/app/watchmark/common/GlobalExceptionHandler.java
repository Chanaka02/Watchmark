package app.watchmark.common;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Single place that turns exceptions into {@code application/problem+json}.
 * Security filter failures are routed here too (see SecurityConfig), so every
 * error a client sees has the same shape. Responses never include stack
 * traces, rejected input values or exception messages from libraries.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static final URI ABOUT_BLANK = URI.create("about:blank");

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
		var response = ResponseEntity.status(ex.status());
		if (ex.retryAfter() != null) {
			response.header(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, ex.retryAfter().toSeconds())));
		}
		return response.body(problem(ex.status(), ex.type(), ex.title(), ex.getMessage()));
	}

	@ExceptionHandler(BadCredentialsException.class)
	ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex) {
		// One message for unknown email and wrong password: no account enumeration.
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(problem(HttpStatus.UNAUTHORIZED, "invalid-credentials", "Invalid credentials",
					"The email or password is incorrect."));
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ProblemDetail> handleUnauthenticated(AuthenticationException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(problem(HttpStatus.UNAUTHORIZED, "unauthenticated", "Authentication required",
					"Sign in to continue."));
	}

	@ExceptionHandler(CsrfException.class)
	ResponseEntity<ProblemDetail> handleCsrf(CsrfException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(problem(HttpStatus.FORBIDDEN, "csrf-token-invalid", "Invalid CSRF token",
					"Refresh the page and try again."));
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", "You cannot perform this action."));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error",
					"Something went wrong. Please try again later."));
	}

	/** Invalid JSON request bodies (@Valid @RequestBody). */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> fieldError(error.getField(), error.getDefaultMessage()))
			.toList();
		return ResponseEntity.badRequest().body(validationProblem(errors));
	}

	/** Invalid query or path parameters (constraints on controller method parameters). */
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = ex.getParameterValidationResults()
			.stream()
			.flatMap(result -> result.getResolvableErrors()
				.stream()
				.map(error -> fieldError(result.getMethodParameter().getParameterName(),
						error.getDefaultMessage())))
			.toList();
		return ResponseEntity.badRequest().body(validationProblem(errors));
	}

	/**
	 * Spring's built-in MVC errors (wrong parameter type, unknown route, wrong
	 * method, …) arrive with type {@code about:blank}; give them a stable slug.
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		if (body instanceof ProblemDetail problem
				&& (problem.getType() == null || ABOUT_BLANK.equals(problem.getType()))) {
			problem.setType(URI.create("/problems/" + slugFor(statusCode)));
		}
		return super.handleExceptionInternal(ex, body, headers, statusCode, request);
	}

	static ProblemDetail problem(HttpStatusCode status, String type, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("/problems/" + type));
		problem.setTitle(title);
		return problem;
	}

	private static ProblemDetail validationProblem(List<Map<String, String>> errors) {
		ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed",
				"One or more fields are invalid.");
		body.setProperty("errors", errors);
		return body;
	}

	private static Map<String, String> fieldError(String field, String message) {
		return Map.of("field", field == null ? "request" : field, "message", message == null ? "is invalid" : message);
	}

	private static String slugFor(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> "bad-request";
			case 404 -> "not-found";
			case 405 -> "method-not-allowed";
			case 406 -> "not-acceptable";
			case 415 -> "unsupported-media-type";
			default -> "http-" + status.value();
		};
	}

}
