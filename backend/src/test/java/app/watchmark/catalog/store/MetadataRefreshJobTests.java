package app.watchmark.catalog.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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

	private long userId;

	@BeforeEach
	void setUp() {
		jdbc.update("DELETE FROM user_shows");
		jdbc.update("DELETE FROM shows");
		jdbc.update("DELETE FROM users WHERE email = 'refresh-job@example.com'");
		jdbc.update("INSERT INTO users (email, display_name, password_hash, time_zone, created_at, updated_at) "
				+ "VALUES ('refresh-job@example.com', 'Job', '{noop}x', 'UTC', ?, ?)", Timestamp.from(NOW),
				Timestamp.from(NOW));
		userId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'refresh-job@example.com'", Long.class);
		provider.reset();
		clock.set(NOW);
	}

	// --- Unreferenced shows ------------------------------------------------------------------------

	@Test
	void unreferencedShowsAreDeletedInsteadOfRefreshed() {
		storeUnreferenced("orphan", ShowStatus.RETURNING);
		store("kept", ShowStatus.RETURNING);
		clock.advance(Duration.ofDays(1));

		MetadataRefreshJob.Report report = job.runOnce();

		assertThat(report.deleted()).isEqualTo(1);
		assertThat(shows.findByProviderAndExternalId("tmdb", "orphan")).isEmpty();
		assertThat(count("SELECT COUNT(*) FROM episodes e JOIN shows s ON s.id = e.show_id WHERE s.external_id = 'orphan'"))
			.as("seasons and episodes cascade")
			.isZero();
		assertThat(report.refreshed()).as("only the referenced show is refreshed").isEqualTo(1);
	}

	@Test
	void recentlyStoredUnreferencedShowsAreKept() {
		storeUnreferenced("just-added", ShowStatus.RETURNING);
		clock.advance(Duration.ofMinutes(30));

		assertThat(job.runOnce().deleted()).isZero();
		assertThat(shows.findByProviderAndExternalId("tmdb", "just-added")).isPresent();
	}

	@Test
	void showsInARemovedLibraryEntryAreStillReferenced() {
		store("removed", ShowStatus.ENDED);
		jdbc.update("UPDATE user_shows SET removed_at = ? WHERE user_id = ?", Timestamp.from(NOW), userId);
		clock.advance(Duration.ofDays(2));

		assertThat(job.runOnce().deleted()).as("history must survive removal (R10)").isZero();
	}

	@Test
	void databaseRefusesToDeleteAReferencedShow() {
		store("referenced", ShowStatus.ENDED);

		assertThatThrownBy(() -> jdbc.update("DELETE FROM shows WHERE external_id = 'referenced'"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	// --- Refresh -----------------------------------------------------------------------------------

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

	/** Stores the show and references it from a library entry, like a real add. */
	private void store(String id, ShowStatus status) {
		storeUnreferenced(id, status);
		jdbc.update("INSERT INTO user_shows (user_id, show_id, status, added_at, updated_at) "
				+ "VALUES (?, ?, 'WATCHING', ?, ?)", userId, stored(id).getId(), Timestamp.from(NOW),
				Timestamp.from(NOW));
	}

	private void storeUnreferenced(String id, ShowStatus status) {
		EpisodeInfo episode = new EpisodeInfo(id + "-e1", 1, 1, "Episode 1", null, LocalDate.of(2026, 10, 1), 30,
				null);
		provider.shows.put(id, new ShowDetails("tmdb:" + id, "Show " + id, null, null, null, status, null, null, null,
				null, List.of(), List.of(), null, 1, 1, null,
				List.of(new SeasonSummary(1, "Season 1", 1, LocalDate.of(2026, 10, 1), null)), null, null, List.of()));
		provider.seasons.put(id + "/1",
				new SeasonDetails("tmdb:" + id, 1, "Season 1", null, null, null, List.of(episode)));
		sync.sync(new ShowRef("tmdb", id));
	}

	private int count(String sql) {
		return jdbc.queryForObject(sql, Integer.class);
	}

	private Show stored(String id) {
		return shows.findByProviderAndExternalId("tmdb", id).orElseThrow();
	}

	private Instant syncedAt(String id) {
		return stored(id).getMetadataSyncedAt();
	}

}
