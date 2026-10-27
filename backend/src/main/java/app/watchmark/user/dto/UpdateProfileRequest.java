package app.watchmark.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import app.watchmark.user.validation.TimeZoneId;

/** Partial update: a {@code null} field is left unchanged. */
public record UpdateProfileRequest(
		@Size(min = 1, max = 50) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String displayName,
		@TimeZoneId String timeZone) {
}
