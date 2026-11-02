package app.watchmark.catalog.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import app.watchmark.catalog.dto.CatalogDtos.SeasonSummary;
import app.watchmark.catalog.store.MetadataWriter.SeasonState;

class RefreshPropertiesTests {

	private static RefreshProperties withHardMaxAge(Duration hardMaxAge) {
		return new RefreshProperties("-", 200, Duration.ofHours(20), Duration.ofDays(30), Duration.ofDays(30),
				hardMaxAge, Duration.ofHours(12));
	}

	@Test
	void hardMaxAgeMustStayWellInsideTheSixMonthLimit() {
		assertThat(withHardMaxAge(Duration.ofDays(150)).hardMaxAge()).isEqualTo(Duration.ofDays(150));
		assertThatThrownBy(() -> withHardMaxAge(Duration.ofDays(175))).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("six-month");
	}

	@Test
	void seasonFetchDecision() {
		var service = new MetadataSyncService(null, null, null, withHardMaxAge(Duration.ofDays(150)), null);
		Instant now = Instant.parse("2026-11-02T10:00:00Z");
		SeasonSummary season = new SeasonSummary(1, "Season 1", 8, LocalDate.of(2020, 1, 1), null);
		SeasonState stable = new SeasonState(8, now.minus(Duration.ofDays(5)), false);

		assertThat(service.needsEpisodes(season, null, 3, now)).as("never stored").isTrue();
		assertThat(service.needsEpisodes(season, new SeasonState(8, null, false), 3, now)).as("never fetched")
			.isTrue();
		assertThat(service.needsEpisodes(season, stable, 3, now)).as("stable old season").isFalse();
		assertThat(service.needsEpisodes(season, stable, 1, now)).as("latest season").isTrue();
		assertThat(service.needsEpisodes(season, new SeasonState(7, stable.episodesSyncedAt(), false), 3, now))
			.as("count changed")
			.isTrue();
		assertThat(service.needsEpisodes(season, new SeasonState(8, stable.episodesSyncedAt(), true), 3, now))
			.as("pending episodes")
			.isTrue();
		assertThat(service.needsEpisodes(season, new SeasonState(8, now.minus(Duration.ofDays(31)), false), 3, now))
			.as("older than season-max-age")
			.isTrue();
	}

}
