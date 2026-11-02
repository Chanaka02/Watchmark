package app.watchmark.catalog.store;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * When stored metadata is refreshed (see docs/tmdb.md for the six-month limit).
 *
 * @param cron when the refresh job runs, in UTC; {@code -} disables it
 * @param batchSize at most this many shows per run
 * @param airingMaxAge refresh shows that may still change after this long
 * @param finishedMaxAge refresh ended, canceled and upstream-missing shows after this long
 * @param seasonMaxAge re-fetch a season's episodes after this long even if nothing hints at a change
 * @param hardMaxAge refresh anything older than this regardless of status; must
 *     stay well under TMDB's six-month caching limit
 * @param retryAfter minimum time between two refresh attempts of the same show
 */
@Validated
@ConfigurationProperties("watchmark.metadata.refresh")
public record RefreshProperties(@NotBlank String cron, @Positive int batchSize, @NotNull Duration airingMaxAge,
		@NotNull Duration finishedMaxAge, @NotNull Duration seasonMaxAge, @NotNull Duration hardMaxAge,
		@NotNull Duration retryAfter) {

	/** TMDB forbids caching its content for longer than this. */
	static final Duration PROVIDER_CACHE_LIMIT = Duration.ofDays(182);

	public RefreshProperties {
		if (hardMaxAge != null && hardMaxAge.compareTo(PROVIDER_CACHE_LIMIT.minusDays(14)) > 0) {
			throw new IllegalArgumentException(
					"watchmark.metadata.refresh.hard-max-age must leave at least 14 days before the six-month limit");
		}
	}

}
