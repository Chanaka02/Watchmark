package app.watchmark.common;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("watchmark.rate-limit")
public record RateLimitProperties(@NotNull @Valid Rule loginPerClient, @NotNull @Valid Rule loginPerAccount,
		@NotNull @Valid Rule registerPerClient, @NotNull @Valid Rule sensitivePerUser,
		@NotNull @Valid Rule searchPerUser) {

	/** At most {@code limit} attempts per fixed {@code window}. */
	public record Rule(@Positive int limit, @NotNull Duration window) {
	}

}
