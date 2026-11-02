package app.watchmark.catalog.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import app.watchmark.MutableClock;
import app.watchmark.TestcontainersConfiguration;
import app.watchmark.catalog.MetadataNotFoundException;
import app.watchmark.catalog.MetadataUnavailableException;
import app.watchmark.catalog.ShowRef;
import app.watchmark.catalog.ShowStatus;
import app.watchmark.catalog.dto.CatalogDtos.EpisodeInfo;
import app.watchmark.catalog.dto.CatalogDtos.SeasonDetails;
import app.watchmark.catalog.dto.CatalogDtos.SeasonSummary;
import app.watchmark.catalog.dto.CatalogDtos.ShowDetails;

/**
 * Metadata persistence against real MySQL: idempotency, in-place updates,
 * episodes dropped/renumbered/replaced upstream (R8), atomicity on provider
 * failure, selective season fetching and concurrent syncs.
 */
@Import({ TestcontainersConfiguration.class, FakeProviderConfig.class })
@SpringBootTest
class MetadataSyncTests {

	static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

	static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

	@Autowired
	MetadataSyncService sync;

	@Autowired
	FakeMetadataProvider provider;

	@Autowired
	MutableClock clock;

	@Autowired
	ShowRepository shows;

	@Autowired
	SeasonRepository seasons;

	@Autowired
	EpisodeRepository episodes;

	@Autowired
	JdbcTemplate jdbc;

	private static final AtomicInteger ids = new AtomicInteger(5000);

	private String id;

	private ShowRef ref;

	@BeforeEach
	void setUp() {
		provider.reset();
		clock.set(NOW);
		id = Integer.toString(ids.incrementAndGet());
		ref = new ShowRef("tmdb", id);
	}

	// --- First sync and idempotency -------------------------------------------------------

	@Test
	void firstSyncStoresShowSeasonsEpisodesAndGenres() {
		publish(ShowStatus.RETURNING, List.of("Drama", "Mystery"),
				season(0, ep("s1", 0, 1, TODAY.minusYears(1))),
				season(1, ep("a1", 1, 1, TODAY.minusDays(30)), ep("a2", 1, 2, TODAY.minusDays(23))));

		SyncOutcome outcome = sync.sync(ref);

		assertThat(outcome.created()).isTrue();
		assertThat(outcome.episodesAdded()).isEqualTo(3);
		Show show = storedShow();
		assertThat(show.getName()).isEqualTo("Show " + id);
		assertThat(show.getStatus()).isEqualTo(ShowStatus.RETURNING);
		assertThat(show.getMetadataSyncedAt()).isEqualTo(NOW);
		assertThat(genreNames()).containsExactlyInAnyOrder("Drama", "Mystery");
		assertThat(seasons.findByShowId(show.getId())).extracting(Season::getSeasonNumber).containsExactly(0, 1);
		assertThat(activeEpisodes()).extracting(Episode::getExternalId).containsExactly("s1", "a1", "a2");
	}

	@Test
	void repeatedSyncIsIdempotent() {
		publish(ShowStatus.RETURNING, List.of("Drama"), season(1, ep("a1", 1, 1, TODAY.minusDays(7))));
		sync.sync(ref);
		List<Long> episodeIds = activeEpisodes().stream().map(Episode::getId).toList();

		clock.advance(Duration.ofDays(1));
		SyncOutcome second = sync.sync(ref);

		assertThat(second.created()).isFalse();
		assertThat(second.episodesAdded()).isZero();
		assertThat(activeEpisodes()).extracting(Episode::getId).isEqualTo(episodeIds);
		assertThat(count("SELECT COUNT(*) FROM shows WHERE external_id = ?")).isEqualTo(1);
		assertThat(genreNames()).containsExactly("Drama");
	}

	@Test
	void changedMetadataIsUpdatedInPlace() {
		publish(ShowStatus.RETURNING, List.of("Drama"), season(1, ep("a1", 1, 1, TODAY.plusDays(3))));
		sync.sync(ref);
		Long episodeId = activeEpisodes().getFirst().getId();

		// Delayed release, renamed episode, show ended, genre changed.
		publish(ShowStatus.ENDED, List.of("Thriller"),
				season(1, new EpisodeInfo("a1", 1, 1, "New Title", null, TODAY.plusDays(10), 42, null)));
		clock.advance(Duration.ofHours(1));
		sync.sync(ref);

		Episode episode = activeEpisodes().getFirst();
		assertThat(episode.getId()).isEqualTo(episodeId);
		assertThat(episode.getName()).isEqualTo("New Title");
		assertThat(episode.getAirDate()).isEqualTo(TODAY.plusDays(10));
		assertThat(episode.getRuntimeMinutes()).isEqualTo(42);
		assertThat(storedShow().getStatus()).isEqualTo(ShowStatus.ENDED);
		assertThat(genreNames()).containsExactly("Thriller");
	}

	// --- R8: episodes and seasons changing upstream -----------------------------------------

	@Test
	void episodeDroppedUpstreamIsMarkedMissingNotDeleted() {
		publish(ShowStatus.ENDED, List.of(), season(1, ep("a1", 1, 1, TODAY.minusDays(30)),
				ep("a2", 1, 2, TODAY.minusDays(23))));
		sync.sync(ref);

		publish(ShowStatus.ENDED, List.of(), season(1, ep("a1", 1, 1, TODAY.minusDays(30))));
		clock.advance(Duration.ofHours(1));
		SyncOutcome outcome = sync.sync(ref);

		assertThat(outcome.episodesMissing()).isEqualTo(1);
		Episode dropped = episodeByExternalId("a2");
		assertThat(dropped.isActive()).isFalse();
		assertThat(dropped.getEpisodeNumber()).as("original number kept for history").isEqualTo(2);
		assertThat(activeEpisodes()).extracting(Episode::getExternalId).containsExactly("a1");
	}

	@Test
	void episodeThatReappearsIsReactivatedWithTheSameRow() {
		publish(ShowStatus.ENDED, List.of(), season(1, ep("a1", 1, 1, TODAY.minusDays(30)),
				ep("a2", 1, 2, TODAY.minusDays(23))));
		sync.sync(ref);
		Long rowId = episodeByExternalId("a2").getId();
		publish(ShowStatus.ENDED, List.of(), season(1, ep("a1", 1, 1, TODAY.minusDays(30))));
		sync.sync(ref);

		publish(ShowStatus.ENDED, List.of(), season(1, ep("a1", 1, 1, TODAY.minusDays(30)),
				ep("a2", 1, 2, TODAY.minusDays(23))));
		sync.sync(ref);

		Episode back = episodeByExternalId("a2");
		assertThat(back.getId()).isEqualTo(rowId);
		assertThat(back.isActive()).isTrue();
	}

	@Test
	void renumberedEpisodesKeepTheirRows() {
		publish(ShowStatus.ENDED, List.of(), season(1, ep("a1", 1, 1, TODAY.minusDays(30)),
				ep("a2", 1, 2, TODAY.minusDays(23)), ep("a3", 1, 3, TODAY.minusDays(16))));
		sync.sync(ref);
		Long a1 = episodeByExternalId("a1").getId();
		Long a3 = episodeByExternalId("a3").getId();

		// Upstream swaps the first and third episode numbers.
		publish(ShowStatus.ENDED, List.of(), season(1, ep("a3", 1, 1, TODAY.minusDays(16)),
				ep("a2", 1, 2, TODAY.minusDays(23)), ep("a1", 1, 3, TODAY.minusDays(30))));
		sync.sync(ref);

		assertThat(episodeByExternalId("a1")).extracting(Episode::getId, Episode::getEpisodeNumber)
			.containsExactly(a1, 3);
		assertThat(episodeByExternalId("a3")).extracting(Episode::getId, Episode::getEpisodeNumber)
			.containsExactly(a3, 1);
		assertThat(activeEpisodes()).hasSize(3).allMatch(Episode::isActive);
	}

	@Test
	void replacedEpisodeInTheSameSlotIsANewRow() {
		publish(ShowStatus.ENDED, List.of(), season(1, ep("old", 1, 1, TODAY.minusDays(30))));
		sync.sync(ref);

		publish(ShowStatus.ENDED, List.of(), season(1, ep("new", 1, 1, TODAY.minusDays(30))));
		sync.sync(ref);

		assertThat(episodeByExternalId("old").isActive()).isFalse();
		assertThat(episodeByExternalId("new").isActive()).isTrue();
		assertThat(episodeByExternalId("new").getEpisodeNumber()).isEqualTo(1);
	}

	@Test
	void episodeMovedToAnotherSeasonKeepsItsRow() {
		publish(ShowStatus.ENDED, List.of(), season(0, ep("x", 0, 1, TODAY.minusDays(60))),
				season(1, ep("a1", 1, 1, TODAY.minusDays(30))));
		sync.sync(ref);
		Long rowId = episodeByExternalId("x").getId();

		// The special is reclassified as episode 2 of season 1.
		publish(ShowStatus.ENDED, List.of(), season(0),
				season(1, ep("a1", 1, 1, TODAY.minusDays(30)), ep("x", 1, 2, TODAY.minusDays(60))));
		sync.sync(ref);

		Episode moved = episodeByExternalId("x");
		assertThat(moved.getId()).isEqualTo(rowId);
		assertThat(moved.isActive()).isTrue();
		assertThat(moved.getSeasonNumber()).isEqualTo(1);
		assertThat(moved.getEpisodeNumber()).isEqualTo(2);
	}

	@Test
	void seasonDroppedUpstreamIsMarkedMissingWithItsEpisodes() {
		publish(ShowStatus.ENDED, List.of(), season(1, ep("a1", 1, 1, TODAY.minusDays(30))),
				season(2, ep("b1", 2, 1, TODAY.minusDays(10))));
		sync.sync(ref);

		publish(ShowStatus.ENDED, List.of(), season(1, ep("a1", 1, 1, TODAY.minusDays(30))));
		sync.sync(ref);

		Season two = seasons.findByShowId(storedShow().getId())
			.stream()
			.filter(season -> season.getSeasonNumber() == 2)
			.findFirst()
			.orElseThrow();
		assertThat(two.isActive()).isFalse();
		assertThat(episodeByExternalId("b1").isActive()).isFalse();
		assertThat(episodeByExternalId("a1").isActive()).isTrue();
	}

	@Test
	void databaseRejectsTwoActiveEpisodesInOneSlotButAllowsMissingDuplicates() {
		publish(ShowStatus.ENDED, List.of(), season(1, ep("a1", 1, 1, TODAY.minusDays(30))));
		sync.sync(ref);
		long showId = storedShow().getId();
		long seasonId = seasons.findByShowId(showId).getFirst().getId();
		String insert = "INSERT INTO episodes (show_id, season_id, season_number, episode_number, external_id, name, "
				+ "upstream_missing_at) VALUES (?, ?, 1, 1, ?, 'dup', ?)";

		jdbc.update(insert, showId, seasonId, "missing-dup", Timestamp.from(NOW));
		assertThatThrownBy(() -> jdbc.update(insert, showId, seasonId, "active-dup", null))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	// --- Provider failures -----------------------------------------------------------------------

	@Test
	void providerFailureMidSyncLeavesStoredDataUntouched() {
		publish(ShowStatus.RETURNING, List.of("Drama"), season(1, ep("a1", 1, 1, TODAY.minusDays(7))),
				season(2, ep("b1", 2, 1, TODAY.plusDays(7))));
		sync.sync(ref);

		// Show details change, but season 2 then fails: nothing may be written.
		publish(ShowStatus.ENDED, List.of("Comedy"), season(1, ep("a1", 1, 1, TODAY.minusDays(7))),
				season(2, ep("b1", 2, 1, TODAY.plusDays(14))));
		provider.unavailable.put(id + "/2", true);
		clock.advance(Duration.ofHours(1));

		assertThatThrownBy(() -> sync.sync(ref)).isInstanceOf(MetadataUnavailableException.class);

		Show show = storedShow();
		assertThat(show.getStatus()).isEqualTo(ShowStatus.RETURNING);
		assertThat(show.getMetadataSyncedAt()).isEqualTo(NOW);
		assertThat(show.getRefreshFailures()).isEqualTo(1);
		assertThat(genreNames()).containsExactly("Drama");
		assertThat(episodeByExternalId("b1").getAirDate()).isEqualTo(TODAY.plusDays(7));
	}

	@Test
	void showRemovedUpstreamIsKeptAndMarkedMissing() {
		publish(ShowStatus.ENDED, List.of(), season(1, ep("a1", 1, 1, TODAY.minusDays(30))));
		sync.sync(ref);
		provider.shows.remove(id);

		SyncOutcome outcome = sync.sync(ref);

		assertThat(outcome.upstreamMissing()).isTrue();
		assertThat(storedShow().getUpstreamMissingAt()).isEqualTo(NOW);
		assertThat(activeEpisodes()).hasSize(1);
	}

	@Test
	void unknownShowThatWasNeverStoredIsNotFound() {
		assertThatThrownBy(() -> sync.sync(ref)).isInstanceOf(MetadataNotFoundException.class);
		assertThat(shows.findByProviderAndExternalId("tmdb", id)).isEmpty();
	}

	@Test
	void refOfAnotherProviderIsRejected() {
		assertThatThrownBy(() -> sync.sync(new ShowRef("mock", "1001"))).isInstanceOf(MetadataNotFoundException.class);
	}

	// --- Selective season fetching ------------------------------------------------------------

	@Test
	void stableOldSeasonsAreNotRefetchedButLatestAndPendingOnesAre() {
		publish(ShowStatus.RETURNING, List.of(), season(1, ep("a1", 1, 1, TODAY.minusYears(2))),
				season(2, ep("b1", 2, 1, TODAY.minusYears(1)), ep("b2", 2, 2, null)),
				season(3, ep("c1", 3, 1, TODAY.minusDays(7))));
		sync.sync(ref);
		assertThat(provider.seasonCalls(id)).as("first sync fetches every season").hasSize(3);

		provider.calls.clear();
		clock.advance(Duration.ofDays(1));
		sync.sync(ref);

		// Season 1: old and complete, skipped. Season 2: has an undated episode. Season 3: latest.
		assertThat(provider.seasonCalls(id)).containsExactly(id + "/2", id + "/3");
	}

	@Test
	void changedEpisodeCountOrAgeTriggersARefetch() {
		publish(ShowStatus.RETURNING, List.of(), season(1, ep("a1", 1, 1, TODAY.minusYears(2))),
				season(2, ep("b1", 2, 1, TODAY.minusYears(1))));
		sync.sync(ref);

		// Season 1 gains an episode upstream (count in the show summary changes).
		publish(ShowStatus.RETURNING, List.of(), season(1, ep("a1", 1, 1, TODAY.minusYears(2)),
				ep("a2", 1, 2, TODAY.minusYears(2).plusDays(7))), season(2, ep("b1", 2, 1, TODAY.minusYears(1))));
		provider.calls.clear();
		sync.sync(ref);
		assertThat(provider.seasonCalls(id)).contains(id + "/1");
		assertThat(episodeByExternalId("a2").isActive()).isTrue();

		// Nothing changes, but the season was last fetched more than 30 days ago.
		provider.calls.clear();
		clock.advance(Duration.ofDays(31));
		sync.sync(ref);
		assertThat(provider.seasonCalls(id)).contains(id + "/1");
	}

	@Test
	void seasonThatIsListedButNotServedKeepsItsEpisodes() {
		publish(ShowStatus.RETURNING, List.of(), season(1, ep("a1", 1, 1, TODAY.minusDays(7))));
		sync.sync(ref);
		provider.seasons.remove(id + "/1");

		sync.sync(ref);

		assertThat(episodeByExternalId("a1").isActive()).isTrue();
	}

	// --- Concurrency ---------------------------------------------------------------------------

	@Test
	void concurrentFirstSyncsStoreTheShowOnce() throws Exception {
		publish(ShowStatus.RETURNING, List.of("Drama"), season(1, ep("a1", 1, 1, TODAY.minusDays(7)),
				ep("a2", 1, 2, TODAY)));
		int threads = 4;
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			List<Future<SyncOutcome>> results = new ArrayList<>();
			Callable<SyncOutcome> task = () -> {
				start.await();
				return sync.sync(ref);
			};
			for (int i = 0; i < threads; i++) {
				results.add(pool.submit(task));
			}
			start.countDown();
			for (Future<SyncOutcome> result : results) {
				result.get();
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(count("SELECT COUNT(*) FROM shows WHERE external_id = ?")).isEqualTo(1);
		assertThat(activeEpisodes()).hasSize(2);
		assertThat(count("SELECT COUNT(*) FROM seasons s JOIN shows sh ON sh.id = s.show_id WHERE sh.external_id = ?"))
			.isEqualTo(1);
	}

	// --- Helpers --------------------------------------------------------------------------------

	private void publish(ShowStatus status, List<String> genres, SeasonDetails... seasonDetails) {
		List<SeasonSummary> summaries = Arrays.stream(seasonDetails)
			.map(details -> new SeasonSummary(details.seasonNumber(), details.name(), details.episodes().size(),
					details.airDate(), null))
			.toList();
		provider.shows.put(id, new ShowDetails(ref.toString(), "Show " + id, null, null, null, status, null, null,
				null, null, genres, List.of(), null, summaries.size(), null, null, summaries, null, null, List.of()));
		provider.seasons.keySet().removeIf(key -> key.startsWith(id + "/"));
		for (SeasonDetails details : seasonDetails) {
			provider.seasons.put(id + "/" + details.seasonNumber(), details);
		}
	}

	private SeasonDetails season(int number, EpisodeInfo... episodeInfos) {
		LocalDate firstAir = Arrays.stream(episodeInfos)
			.map(EpisodeInfo::airDate)
			.filter(Objects::nonNull)
			.min(LocalDate::compareTo)
			.orElse(null);
		return new SeasonDetails(ref.toString(), number, number == 0 ? "Specials" : "Season " + number, null,
				firstAir, null, List.of(episodeInfos));
	}

	private static EpisodeInfo ep(String externalId, int season, int number, LocalDate airDate) {
		return new EpisodeInfo(externalId, season, number, "Episode " + number, null, airDate, 45, null);
	}

	private Show storedShow() {
		return shows.findByProviderAndExternalId("tmdb", id).orElseThrow();
	}

	private List<Episode> activeEpisodes() {
		return episodes.findByShowId(storedShow().getId()).stream().filter(Episode::isActive).toList();
	}

	private Episode episodeByExternalId(String externalId) {
		return episodes.findByShowId(storedShow().getId())
			.stream()
			.filter(episode -> externalId.equals(episode.getExternalId()))
			.findFirst()
			.orElseThrow();
	}

	private List<String> genreNames() {
		return jdbc.queryForList("SELECT g.name FROM genres g JOIN show_genres sg ON sg.genre_id = g.id "
				+ "JOIN shows s ON s.id = sg.show_id WHERE s.external_id = ? ORDER BY g.name", String.class, id);
	}

	private int count(String sql) {
		return jdbc.queryForObject(sql, Integer.class, id);
	}

}
