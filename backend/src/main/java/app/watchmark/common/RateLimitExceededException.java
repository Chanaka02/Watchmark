package app.watchmark.common;

import java.time.Duration;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends ApiException {

	public RateLimitExceededException(Duration retryAfter) {
		super(HttpStatus.TOO_MANY_REQUESTS, "rate-limited", "Too many requests",
				"Too many attempts. Please wait before trying again.", retryAfter);
	}

}
