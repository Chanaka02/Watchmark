package app.watchmark.common;

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

	public ApiException(HttpStatus status, String type, String title, String detail) {
		super(detail);
		this.status = status;
		this.type = type;
		this.title = title;
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

	public static ApiException notFound(String detail) {
		return new ApiException(HttpStatus.NOT_FOUND, "not-found", "Not found", detail);
	}

}
