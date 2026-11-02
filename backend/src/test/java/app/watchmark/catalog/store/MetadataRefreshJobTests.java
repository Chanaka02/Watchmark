package app.watchmark.catalog.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

import app.watchmark.MutableClock;
import app.watchmark.TestcontainersConfiguration;
import app.watchmark.catalog.ShowRef;
import app.watchmark.catalog.ShowStatus;
import app.watchmark.catalog.dto.CatalogDtos.EpisodeInfo;
import app.watchmark.catalog.dto.CatalogDtos.SeasonDetails;
import app.watchmark.catalog.dto.CatalogDtos.SeasonSummary;
import app.watchmark.catalog.dto.CatalogDtos.ShowDetails;

/** Which stored shows the daily job refreshes, and how it reports failures. */
@Import({ TestcontainersConfiguration.class, MetadataSyncTests.Config.class })
@SpringBootTest
class MetadataRefreshJobTests {

	private static final Instant NOW = MetadataSyncTests.NOW;

	@Autowired
	MetadataRefreshJob job;

	@Autowired
	MetadataSyncService sync;

	@Autowired
	FakeMetadataProvider provider;

	@Autowired
	MutableClock clock;

	@Autowired
	ShowRepository shows;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		jdbc.update("DELETE FROM shows");
		provider.reset();
		clock.set(NOW);
	}

	@Test
	void airingShowsAreRefreshedDailyAndFinishedOnesMonthly() {
		store("1", ShowStatus.RETURNING);
		store("2", ShowStatus.ENDED);
		store("3", ShowStatus.CANCELED);

		clock.advance(Duration.ofHours(21));
		assertThat(job.runOnce()).extracting(MetadataRefreshJob.Report::due, MetadataRefreshJob.Report::refreshed)
			.containsExactly(1, 1);
		assertThat(syncedAt("1")).isEqualTo(clock.instant());
		assertThat(syncedAt("2")).isEqualTo(NOW);

		clock.set(NOW.plus(Duration.ofDays(31)));
		assertThat(job.runOnce().refreshed()).isEqualTo(3);
	}

	@Test
	void freshShowsAreLeftAlone() {
		store("1", ShowStatus.RETURNING);
		clock.advance(Duration.ofHours(19));

		assertThat(job.runOnce().due()).isZero();
		assertThat(provider.showCalls.get()).isEqualTo(1);
	}

	@Test
	void failedRefreshKeepsDataAndIsNotRetriedImmediately() {
		store("1", ShowStatus.RETURNING);
		provider.unavailable.put("1", true);
		clock.advance(Duration.ofDays(1));

		MetadataRefreshJob.Report first = job.runOnce();
		assertThat(first.failed()).isEqualTo(1);
		assertThat(stored("1").getRefreshFailures()).isEqualTo(1);
		assertThat(syncedAt("1")).as("old data kept").isEqualTo(NOW);

		// Within retry-after (12 h) the show is skipped; afterwards it is retried.
		clock.advance(Duration.ofHours(6));
		assertThat(job.runOnce().due()).isZero();
		clock.advance(Duration.ofHours(7));
		provider.unavailable.clear();
		assertThat(job.runOnce().refreshed()).isEqualTo(1);
		assertThat(stored("1").getRefreshFailures()).isZero();
	}

	@Test
	void showsThatCannotBeRefreshedBeforeTheHardLimitAreReported() {
		store("1", ShowStatus.ENDED);
		provider.unavailable.put("1", true);
		clock.set(NOW.plus(Duration.ofDays(151)));

		MetadataRefreshJob.Report report = job.runOnce();

		assertThat(report.failed()).isEqualTo(1);
		assertThat(report.olderThanHardLimit()).isEqualTo(1);
	}

	@Test
	void showRemovedUpstreamIsMarkedMissingAndThenCheckedOnlyMonthly() {
		store("1", ShowStatus.RETURNING);
		provider.shows.remove("1");
		clock.advance(Duration.ofDays(1));

		assertThat(job.runOnce().missing()).isEqualTo(1);
		assertThat(stored("1").getUpstreamMissingAt()).isNotNull();

		clock.advance(Duration.ofDays(2));
		assertThat(job.runOnce().due()).as("missing shows are treated like finished ones").isZero();
	}

	@Test
	void batchSizeIsRespectedOldestFirst() {
		for (int i = 1; i <= 3; i++) {
			store(Integer.toString(i), ShowStatus.RETURNING);
			clock.advance(Duration.ofMinutes(1));
		}
		clock.advance(Duration.ofDays(1));

		// RefreshProperties.batchSize is 200 by default; check ordering via the query directly.
		List<String> due = shows.findDueForRefresh("tmdb", List.of(ShowStatus.ENDED, ShowStatus.CANCELED),
				clock.instant().minus(Duration.ofHours(20)), clock.instant().minus(Duration.ofDays(30)),
				clock.instant().minus(Duration.ofDays(150)), clock.instant().minus(Duration.ofHours(12)),
				Pageable.ofSize(2));
		assertThat(due).containsExactly("1", "2");
	}

	private void store(String id, ShowStatus status) {
		EpisodeInfo episode = new EpisodeInfo(id + "-e1", 1, 1, "Episode 1", null, LocalDate.of(2026, 10, 1), 30,
				null);
		provider.shows.put(id, new ShowDetails("tmdb:" + id, "Show " + id, null, null, null, status, null, null, null,
				null, List.of(), List.of(), null, 1, 1, null,
				List.of(new SeasonSummary(1, "Season 1", 1, LocalDate.of(2026, 10, 1), null)), null, null, List.of()));
		provider.seasons.put(id + "/1",
				new SeasonDetails("tmdb:" + id, 1, "Season 1", null, null, null, List.of(episode)));
		sync.sync(new ShowRef("tmdb", id));
	}

	private Show stored(String id) {
		return shows.findByProviderAndExternalId("tmdb", id).orElseThrow();
	}

	private Instant syncedAt(String id) {
		return stored(id).getMetadataSyncedAt();
	}

}
