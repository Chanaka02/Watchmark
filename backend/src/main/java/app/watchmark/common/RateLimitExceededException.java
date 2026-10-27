package app.watchmark.common;

import java.time.Duration;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends ApiException {

	private final Duration retryAfter;

	public RateLimitExceededException(Duration retryAfter) {
		super(HttpStatus.TOO_MANY_REQUESTS, "rate-limited", "Too many requests",
				"Too many attempts. Please wait before trying again.");
		this.retryAfter = retryAfter;
	}

	public Duration retryAfter() {
		return retryAfter;
	}

}
