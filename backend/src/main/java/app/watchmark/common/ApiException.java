package app.watchmark.common;

import java.time.Duration;

import org.springframework.http.HttpStatus;

/**
 * A business-rule failure that maps directly to an RFC 9457 problem response.
 * The {@code type} is a stable slug clients may switch on; {@code detail} is
 * safe to show to the user and must never contain secrets or internal state.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String type;

	private final String title;

	private final Duration retryAfter;

	public ApiException(HttpStatus status, String type, String title, String detail) {
		this(status, type, title, detail, null);
	}

	/** @param retryAfter sent as a {@code Retry-After} header when not {@code null} */
	public ApiException(HttpStatus status, String type, String title, String detail, Duration retryAfter) {
		super(detail);
		this.status = status;
		this.type = type;
		this.title = title;
		this.retryAfter = retryAfter;
	}

	public HttpStatus status() {
		return status;
	}

	public String type() {
		return type;
	}

	public String title() {
		return title;
	}

	public Duration retryAfter() {
		return retryAfter;
	}

	public static ApiException notFound(String detail) {
		return new ApiException(HttpStatus.NOT_FOUND, "not-found", "Not found", detail);
	}

}
