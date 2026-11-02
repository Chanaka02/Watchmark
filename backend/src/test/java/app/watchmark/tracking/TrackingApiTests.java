package app.watchmark.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import app.watchmark.catalog.ShowStatus;

/**
 * Watch, unwatch, bulk actions and undo over HTTP: rules R1 (isolation), R3
 * (reversibility), R4 (no duplicates) and R5 (eligibility).
 */
class TrackingApiTests extends TrackingTestSupport {

	User alice;

	User bob;

	String show;

	@BeforeEach
	void setUp() {
		alice = newUser();
		bob = newUser();
		show = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(21)), ep(1, 2, TODAY.minusDays(14)),
				ep(1, 3, TODAY.minusDays(7)), ep(1, 4, TODAY.plusDays(7)));
		addToLibrary(alice, show, "WATCHING");
	}

	// --- Watch and unwatch -------------------------------------------------------------------------

	@Test
	void watchingRecordsOneOperationAndOneRow() {
		MvcTestResult result = watch(alice, episodeId(show, 1, 1));

		assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"showRef": "%s", "created": 1, "alreadyWatched": 0, "notEligible": 0}
				""".formatted(show));
		assertThat(UUID.fromString(operationId(result))).isNotNull();
		assertThat(activeWatches(alice)).isEqualTo(1);
		assertThat(operations(alice)).isEqualTo(1);
	}

	@Test
	void unwatchingSoftDeletesTheRow() {
		long episode = episodeId(show, 1, 1);
		watch(alice, episode);

		MvcTestResult result = unwatch(alice, episode);

		assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"removed\": 1}");
		assertThat(activeWatches(alice)).isZero();
		assertThat(allWatchRows(alice)).as("kept for undo, purged later (R12)").isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM episode_watches WHERE user_id = ? AND deleted_by_op = UUID_TO_BIN(?)",
				alice.id(), operationId(result)))
			.isEqualTo(1);
	}

	@Test
	void unwatchingSomethingNotWatchedIsANoOp() {
		MvcTestResult result = unwatch(alice, episodeId(show, 1, 1));

		assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"removed\": 0, \"operationId\": null}");
		assertThat(operations(alice)).isZero();
	}

	@Test
	void unknownEpisodesAreNotFound() {
		assertThat(watch(alice, 987_654_321L)).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/episode-not-found");
		assertThat(unwatch(alice, 987_654_321L)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(watchUpTo(alice, 987_654_321L)).hasStatus(HttpStatus.NOT_FOUND);
	}

	// --- R4: no duplicate progress -----------------------------------------------------------------

	@Test
	void watchingTwiceIsIdempotent() {
		long episode = episodeId(show, 1, 1);
		watch(alice, episode);

		MvcTestResult again = watch(alice, episode);

		assertThat(again).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"operationId": null, "created": 0, "alreadyWatched": 1}
				""");
		assertThat(activeWatches(alice)).isEqualTo(1);
		assertThat(operations(alice)).as("no operation for a no-op").isEqualTo(1);
	}

	@Test
	void concurrentWatchesOfTheSameEpisodeCreateOneRow() throws Exception {
		long episode = episodeId(show, 1, 2);
		ExecutorService pool = Executors.newFixedThreadPool(4);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<MvcTestResult>> results = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				results.add(pool.submit(() -> {
					start.await();
					return watch(alice, episode);
				}));
			}
			start.countDown();
			for (Future<MvcTestResult> result : results) {
				assertThat(result.get().getResponse().getStatus()).isEqualTo(200);
			}
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(activeWatches(alice)).isEqualTo(1);
		assertThat(operations(alice)).isEqualTo(1);
	}

	@Test
	void databaseRejectsASecondActiveWatchInTheSameCycle() {
		long episode = episodeId(show, 1, 1);
		watch(alice, episode);

		org.assertj.core.api.Assertions
			.assertThatThrownBy(() -> jdbc.update("""
					INSERT INTO episode_watches (user_id, episode_id, cycle, watched_at, created_by_op)
					SELECT user_id, episode_id, cycle, watched_at, created_by_op FROM episode_watches WHERE user_id = ?
					""", alice.id()))
			.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}

	@Test
	void bulkActionsSkipWatchedAndUnairedEpisodes() {
		watch(alice, episodeId(show, 1, 2));

		MvcTestResult result = watchSeason(alice, show, 1);

		assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"created": 2, "alreadyWatched": 1, "notEligible": 1}
				""");
		assertThat(activeWatches(alice)).isEqualTo(3);
	}

	@Test
	void watchUpToMarksEarlierEpisodes() {
		MvcTestResult result = watchUpTo(alice, episodeId(show, 1, 2));

		assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"created\": 2, \"alreadyWatched\": 0}");
		assertThat(count("SELECT COUNT(*) FROM episode_watches w JOIN episodes e ON e.id = w.episode_id "
				+ "WHERE w.user_id = ? AND e.episode_number IN (1, 2)", alice.id()))
			.isEqualTo(2);
	}

	@Test
	void bulkActionWithNothingToDoRecordsNoOperation() {
		watchSeason(alice, show, 1);
		int before = operations(alice);

		assertThat(watchSeason(alice, show, 1)).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"operationId\": null, \"created\": 0, \"alreadyWatched\": 3}");
		assertThat(operations(alice)).isEqualTo(before);
	}

	@Test
	void markingAnUnknownSeasonIsNotFound() {
		assertThat(watchSeason(alice, show, 9)).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/season-not-found");
		assertThat(watchSeason(alice, "tmdb:999999", 1)).hasStatus(HttpStatus.NOT_FOUND);
	}

	// --- R5: eligibility ---------------------------------------------------------------------------

	@Test
	void unairedEpisodesCannotBeWatched() {
		MvcTestResult result = watch(alice, episodeId(show, 1, 4));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/episode-not-aired");
		assertThat(activeWatches(alice)).isZero();
		assertThat(operations(alice)).isZero();
	}

	@Test
	void airedTodayDependsOnTheUsersTimeZone() {
		String premiere = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.plusDays(1)));
		store(premiere);
		long episode = episodeId(premiere, 1, 1);
		// 2026-11-02T12:00Z is already 3 November in Auckland but still 2 November in Los Angeles.
		clock.set(NOW.plus(Duration.ofHours(2)));
		setTimeZone(alice, "Pacific/Auckland");
		setTimeZone(bob, "America/Los_Angeles");

		assertThat(watch(alice, episode)).hasStatusOk();
		assertThat(watch(bob, episode)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
	}

	@Test
	void unknownDatesAreWatchableOnlyWithEvidenceTheyAired() {
		String gaps = publish(ShowStatus.ENDED, ep(1, 1, null), ep(1, 2, TODAY.minusDays(3)), ep(1, 3, null));
		addToLibrary(alice, gaps, "WATCHING");

		assertThat(watch(alice, episodeId(gaps, 1, 1))).as("a later episode aired").hasStatusOk();
		assertThat(watch(alice, episodeId(gaps, 1, 3))).as("no later episode has aired")
			.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
	}

	@Test
	void specialsFollowTheSameEligibilityRule() {
		String specials = publish(ShowStatus.RETURNING, ep(0, 1, TODAY.minusDays(30)), ep(0, 2, TODAY.plusDays(3)),
				ep(1, 1, TODAY.minusDays(60)));
		addToLibrary(alice, specials, "WATCHING");

		assertThat(watch(alice, episodeId(specials, 0, 1))).hasStatusOk();
		assertThat(watch(alice, episodeId(specials, 0, 2))).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(watchSeason(alice, specials, 0)).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"created\": 0, \"alreadyWatched\": 1, \"notEligible\": 1}");
	}

	// --- R3: undo ----------------------------------------------------------------------------------

	@Test
	void undoingAWatchRemovesItsRow() {
		String op = operationId(watch(alice, episodeId(show, 1, 1)));

		MvcTestResult result = undo(alice, op);

		assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"operationId": "%s", "type": "WATCH", "showRef": "%s", "reverted": 1}
				""".formatted(op, show));
		assertThat(activeWatches(alice)).isZero();
		assertThat(operations(alice)).as("undo does not create an operation").isEqualTo(1);
	}

	@Test
	void undoingAnUnwatchRestoresTheOriginalWatchTime() {
		long episode = episodeId(show, 1, 1);
		watch(alice, episode);
		clock.advance(Duration.ofHours(3));
		String op = operationId(unwatch(alice, episode));

		assertThat(undo(alice, op)).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"type\": \"UNWATCH\", \"reverted\": 1}");

		assertThat(activeWatches(alice)).isEqualTo(1);
		assertThat(read(seasonProgress(alice, show, 1), "$.episodes[0].watchedAt").toString())
			.isEqualTo(NOW.toString());
		assertThat(count("SELECT COUNT(*) FROM episode_watches WHERE user_id = ? AND deleted_by_op IS NOT NULL",
				alice.id()))
			.isZero();
	}

	@Test
	void undoingABulkActionRevertsOnlyTheRowsItCreated() {
		watch(alice, episodeId(show, 1, 1));
		String op = operationId(watchSeason(alice, show, 1));

		assertThat(undo(alice, op)).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"type\": \"SEASON\", \"reverted\": 2}");

		assertThat(activeWatches(alice)).as("the episode watched before stays watched").isEqualTo(1);
	}

	@Test
	void undoingWatchUpTo() {
		String op = operationId(watchUpTo(alice, episodeId(show, 1, 3)));

		assertThat(undo(alice, op)).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"type\": \"UP_TO\", \"reverted\": 3}");
		assertThat(activeWatches(alice)).isZero();
	}

	@Test
	void undoIsAllowedForTheConfiguredWindowOnly() {
		String recent = operationId(watch(alice, episodeId(show, 1, 1)));
		String old = operationId(watch(alice, episodeId(show, 1, 2)));
		clock.advance(Duration.ofHours(23).plusMinutes(59));

		assertThat(undo(alice, recent)).as("23h59m").hasStatusOk();

		clock.set(NOW.plus(Duration.ofHours(24)).plusSeconds(1));
		assertThat(undo(alice, old)).as("24h00m01s").hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/undo-window-expired");
		assertThat(activeWatches(alice)).isEqualTo(1);
	}

	@Test
	void undoAtExactlyTheWindowIsStillAllowed() {
		String op = operationId(watch(alice, episodeId(show, 1, 1)));
		clock.advance(Duration.ofHours(24));

		assertThat(undo(alice, op)).hasStatusOk();
	}

	@Test
	void anOperationCanBeUndoneOnce() {
		String op = operationId(watch(alice, episodeId(show, 1, 1)));
		undo(alice, op);

		assertThat(undo(alice, op)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/already-undone");
	}

	@Test
	void restoringAnEpisodeWatchedAgainConflictsAndChangesNothing() {
		long e1 = episodeId(show, 1, 1);
		long e2 = episodeId(show, 1, 2);
		watchSeason(alice, show, 1);
		String unwatchE1 = operationId(unwatch(alice, e1));
		watch(alice, e1);
		unwatch(alice, e2);

		assertThat(undo(alice, unwatchE1)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/conflicting-watch");
		assertThat(activeWatches(alice)).isEqualTo(2);
		assertThat(count("SELECT COUNT(*) FROM tracking_operations WHERE undone_at IS NOT NULL AND user_id = ?",
				alice.id()))
			.isZero();
	}

	@Test
	void malformedOrUnknownOperationsAreNotFound() {
		assertThat(undo(alice, UUID.randomUUID().toString())).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/operation-not-found");
		assertThat(undo(alice, "not-a-uuid")).hasStatus(HttpStatus.NOT_FOUND);
	}

	// --- R1: isolation -----------------------------------------------------------------------------

	@Test
	void usersCannotSeeOrChangeEachOthersTracking() {
		long episode = episodeId(show, 1, 1);
		String op = operationId(watch(alice, episode));

		assertThat(undo(bob, op)).as("another user's operation").hasStatus(HttpStatus.NOT_FOUND);
		assertThat(unwatch(bob, episode)).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"removed\": 0}");
		assertThat(progress(bob, show)).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/not-in-library");
		assertThat(seasonProgress(bob, show, 1)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(activeWatches(alice)).isEqualTo(1);

		assertThat(watch(bob, episode)).hasStatusOk();
		assertThat(read(progress(alice, show), "$.overall.watched").toString()).isEqualTo("1");
		assertThat(read(progress(bob, show), "$.overall.watched").toString()).isEqualTo("1");
	}

	@Test
	void trackingRequiresSignInAndCsrf() {
		long episode = episodeId(show, 1, 1);

		assertThat(mvc.put()
			.uri("/api/v1/tracking/episodes/" + episode + "/watch")
			.with(alice.client().csrf())
			.with(alice.client().fromClient())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.put()
			.uri("/api/v1/tracking/episodes/" + episode + "/watch")
			.cookie(alice.session())
			.with(alice.client().fromClient())).as("no CSRF token").hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/v1/library/" + show + "/progress")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

}
