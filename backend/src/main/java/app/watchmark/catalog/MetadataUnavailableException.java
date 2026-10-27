package app.watchmark.catalog;

import java.time.Duration;

import org.springframework.http.HttpStatus;

import app.watchmark.common.ApiException;

/**
 * The metadata provider failed or could not be reached. Reported to clients as
 * {@code 503} without upstream details; providers log the cause where it occurs.
 */
public class MetadataUnavailableException extends ApiException {

	/** @param retryAfter how long the provider asked us to wait; nullable */
	public MetadataUnavailableException(Duration retryAfter, Throwable cause) {
		super(HttpStatus.SERVICE_UNAVAILABLE, "metadata-unavailable", "Show data unavailable",
				"Show information is temporarily unavailable. Please try again shortly.", retryAfter);
		if (cause != null) {
			initCause(cause);
		}
	}

}
