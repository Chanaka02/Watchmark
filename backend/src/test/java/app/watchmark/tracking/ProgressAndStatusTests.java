package app.watchmark.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import app.watchmark.catalog.ShowStatus;

/**
 * Progress, season states, status automation and rewatch: rules R2, R6–R11.
 */
class ProgressAndStatusTests extends TrackingTestSupport {

	User alice;

	User bob;

	@BeforeEach
	void setUp() {
		alice = newUser();
		bob = newUser();
	}

	// --- Progress (R6, R7) -------------------------------------------------------------------------

	@Test
	void progressReportsSeasonsStatesAndNextEpisode() {
		String show = publish(ShowStatus.RETURNING, ep(0, 1, TODAY.minusDays(90)), ep(1, 1, TODAY.minusDays(70)),
				ep(1, 2, TODAY.minusDays(63)), ep(2, 1, TODAY.minusDays(14)), ep(2, 2, TODAY.minusDays(7)),
				ep(2, 3, TODAY.plusDays(7)));
		addToLibrary(alice, show, "WATCHING");
		watchSeason(alice, show, 1);
		watch(alice, episodeId(show, 0, 1));

		assertThat(progress(alice, show)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"showRef": "%s", "cycle": 1, "status": "WATCHING", "canRewatch": true,
				 "overall": {"watched": 2, "eligible": 4, "total": 5, "percent": 50.0, "state": "IN_PROGRESS"},
				 "history": {"uniqueEpisodes": 3, "totalWatches": 3},
				 "seasons": [
				   {"number": 0, "special": true, "watched": 1, "eligible": 1, "total": 1, "state": "COMPLETE"},
				   {"number": 1, "special": false, "watched": 2, "eligible": 2, "total": 2, "state": "COMPLETE"},
				   {"number": 2, "special": false, "watched": 0, "eligible": 2, "total": 3, "state": "NOT_STARTED"}],
				 "nextEpisode": {"season": 2, "episode": 1, "name": "Episode 1"}}
				""".formatted(show));
		assertThat(read(progress(alice, show), "$.nextEpisode.id").toString())
			.isEqualTo(Long.toString(episodeId(show, 2, 1)));
	}

	@Test
	void seasonProgressListsEpisodesWithTheirTrackingState() {
		String show = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(7)), ep(1, 2, null),
				ep(1, 3, TODAY.plusDays(7)));
		addToLibrary(alice, show, "WATCHING");
		watch(alice, episodeId(show, 1, 1));

		assertThat(seasonProgress(alice, show, 1)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"number": 1, "special": false, "watched": 1, "eligible": 1, "total": 3, "state": "CAUGHT_UP",
				 "episodes": [
				   {"episode": 1, "externalId": "%1$s-1-1", "eligible": true, "watched": true,
				    "watchedAt": "2026-11-02T10:00:00Z", "upstreamMissing": false},
				   {"episode": 2, "airDate": null, "eligible": false, "watched": false, "watchedAt": null},
				   {"episode": 3, "eligible": false, "watched": false}]}
				""".formatted(show.substring(5)));
		assertThat(seasonProgress(alice, show, 5)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void anEmptyShowHasZeroProgress() {
		String show = publish(ShowStatus.PLANNED);
		addToLibrary(alice, show, "PLAN_TO_WATCH");

		assertThat(progress(alice, show)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"overall": {"watched": 0, "eligible": 0, "total": 0, "percent": 0.0, "state": "NOT_STARTED"},
				 "seasons": [], "nextEpisode": null, "canRewatch": false}
				""");
	}

	// --- R2: unique episodes vs. total watches ------------------------------------------------------

	@Test
	void rewatchingCountsAgainInTotalsButNotInUniqueEpisodes() {
		String show = publish(ShowStatus.ENDED, ep(1, 1, TODAY.minusDays(30)), ep(1, 2, TODAY.minusDays(23)));
		addToLibrary(alice, show, "WATCHING");
		watchSeason(alice, show, 1);

		assertThat(rewatch(alice, show)).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"status\": \"WATCHING\", \"cycle\": 2}");
		assertThat(progress(alice, show)).bodyJson()
			.isLenientlyEqualTo("{\"cycle\": 2, \"overall\": {\"watched\": 0, \"eligible\": 2},"
					+ " \"history\": {\"uniqueEpisodes\": 2, \"totalWatches\": 2}}");

		watchSeason(alice, show, 1);
		assertThat(progress(alice, show)).bodyJson()
			.isLenientlyEqualTo("{\"overall\": {\"watched\": 2}, \"history\": {\"uniqueEpisodes\": 2, \"totalWatches\": 4}}");
		assertThat(count("SELECT COUNT(*) FROM episode_watches WHERE user_id = ? AND cycle = 1 AND deleted_at IS NULL",
				alice.id()))
			.as("earlier cycle untouched")
			.isEqualTo(2);
	}

	@Test
	void softDeletedRowsAreNotCounted() {
		String show = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(30)), ep(1, 2, TODAY.minusDays(23)));
		addToLibrary(alice, show, "WATCHING");
		watchSeason(alice, show, 1);
		unwatch(alice, episodeId(show, 1, 2));

		assertThat(progress(alice, show)).bodyJson()
			.isLenientlyEqualTo("{\"history\": {\"uniqueEpisodes\": 1, \"totalWatches\": 1}}");
	}

	@Test
	void userTotalsIncludeRemovedShowsAndExcludeSoftDeletedRows() {
		String kept = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(30)), ep(1, 2, TODAY.minusDays(23)));
		String removed = publish(ShowStatus.ENDED, ep(1, 1, TODAY.minusDays(30)));
		store(removed);
		addToLibrary(alice, kept, "WATCHING");
		watchSeason(alice, kept, 1);
		unwatch(alice, episodeId(kept, 1, 2));
		watch(alice, episodeId(removed, 1, 1));
		rewatch(alice, removed);
		watch(alice, episodeId(removed, 1, 1));
		deleteFromLibrary(alice, removed);

		assertThat(history.totals(alice.id())).as("R2 + R10: removed show still counted")
			.isEqualTo(new WatchHistory.Totals(2, 3));
		assertThat(history.totals(bob.id())).isEqualTo(new WatchHistory.Totals(0, 0));
	}

	@org.springframework.beans.factory.annotation.Autowired
	WatchHistory history;

	@Test
	void rewatchNeedsAWatchInTheCurrentCycle() {
		String show = publish(ShowStatus.ENDED, ep(1, 1, TODAY.minusDays(30)));
		addToLibrary(alice, show, "COMPLETED");

		assertThat(rewatch(alice, show)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/nothing-to-rewatch");
		assertThat(rewatch(bob, show)).hasStatus(HttpStatus.NOT_FOUND);
	}

	// --- R9: status automation ---------------------------------------------------------------------

	@Test
	void trackingAShowNotInTheLibraryAddsItAsWatching() {
		String show = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(7)), ep(1, 2, TODAY.plusDays(7)));
		store(show);

		watch(alice, episodeId(show, 1, 1));

		assertThat(libraryStatus(alice, show)).isEqualTo("WATCHING");
	}

	@Test
	void trackingARemovedShowRestoresItsEntry() {
		String show = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(7)), ep(1, 2, TODAY.minusDays(1)));
		addToLibrary(alice, show, "ON_HOLD");
		send("PUT", alice, "/api/v1/library/" + show + "/rating", "{\"rating\": 7}");
		deleteFromLibrary(alice, show);

		watch(alice, episodeId(show, 1, 1));

		assertThat(entry(alice, show)).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"status\": \"ON_HOLD\", \"rating\": 7}");
	}

	@Test
	void planToWatchBecomesWatching() {
		String show = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(7)), ep(1, 2, TODAY.minusDays(1)));
		addToLibrary(alice, show, "PLAN_TO_WATCH");

		watch(alice, episodeId(show, 1, 1));

		assertThat(libraryStatus(alice, show)).isEqualTo("WATCHING");
	}

	@Test
	void finishingAnEndedShowCompletesItAndUnwatchingReopensIt() {
		String show = publish(ShowStatus.ENDED, ep(1, 1, TODAY.minusDays(14)), ep(1, 2, TODAY.minusDays(7)));
		addToLibrary(alice, show, "WATCHING");

		String op = operationId(watchSeason(alice, show, 1));
		assertThat(libraryStatus(alice, show)).isEqualTo("COMPLETED");

		unwatch(alice, episodeId(show, 1, 2));
		assertThat(libraryStatus(alice, show)).isEqualTo("WATCHING");

		watch(alice, episodeId(show, 1, 2));
		assertThat(libraryStatus(alice, show)).isEqualTo("COMPLETED");
		undo(alice, op);
		assertThat(libraryStatus(alice, show)).as("undo is a tracking change too").isEqualTo("WATCHING");
	}

	@Test
	void aReturningShowStaysWatchingWhenCaughtUp() {
		String show = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(14)), ep(1, 2, TODAY.minusDays(7)));
		addToLibrary(alice, show, "WATCHING");

		watchSeason(alice, show, 1);

		assertThat(libraryStatus(alice, show)).isEqualTo("WATCHING");
		assertThat(progress(alice, show)).bodyJson()
			.isLenientlyEqualTo("{\"overall\": {\"state\": \"COMPLETE\"}, \"nextEpisode\": null}");
	}

	@Test
	void onHoldAndDroppedAreNeverChangedAutomatically() {
		String show = publish(ShowStatus.ENDED, ep(1, 1, TODAY.minusDays(14)));
		String other = publish(ShowStatus.ENDED, ep(1, 1, TODAY.minusDays(14)));
		addToLibrary(alice, show, "ON_HOLD");
		addToLibrary(alice, other, "DROPPED");

		watch(alice, episodeId(show, 1, 1));
		watch(alice, episodeId(other, 1, 1));

		assertThat(libraryStatus(alice, show)).isEqualTo("ON_HOLD");
		assertThat(libraryStatus(alice, other)).isEqualTo("DROPPED");
	}

	@Test
	void ineligibleWatchAddsNothingToTheLibrary() {
		String show = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.plusDays(7)));
		store(show);

		assertThat(watch(alice, episodeId(show, 1, 1))).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(count("SELECT COUNT(*) FROM user_shows WHERE user_id = ?", alice.id())).isZero();
	}

	// --- R8: episodes removed upstream -------------------------------------------------------------

	@Test
	void episodesDroppedUpstreamKeepTheirWatchesButLeaveProgress() {
		String show = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(21)), ep(1, 2, TODAY.minusDays(14)),
				ep(1, 3, TODAY.minusDays(7)));
		addToLibrary(alice, show, "WATCHING");
		watchSeason(alice, show, 1);
		long dropped = episodeId(show, 1, 2);
		String entryBefore = jdbc.queryForObject(
				"SELECT CONCAT(status, '|', updated_at, '|', version) FROM user_shows WHERE user_id = ?", String.class,
				alice.id());

		publishAs(show.substring(5), ShowStatus.RETURNING,
				List.of(ep(1, 1, TODAY.minusDays(21)), ep(1, 3, TODAY.minusDays(7))));
		store(show);

		assertThat(progress(alice, show)).bodyJson()
			.isLenientlyEqualTo("{\"overall\": {\"watched\": 2, \"eligible\": 2, \"total\": 2},"
					+ " \"history\": {\"uniqueEpisodes\": 3, \"totalWatches\": 3}}");
		assertThat(count("SELECT COUNT(*) FROM episode_watches WHERE episode_id = ? AND deleted_at IS NULL", dropped))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject(
				"SELECT CONCAT(status, '|', updated_at, '|', version) FROM user_shows WHERE user_id = ?", String.class,
				alice.id()))
			.as("a refresh never changes library entries")
			.isEqualTo(entryBefore);

		assertThat(seasonProgress(alice, show, 1)).bodyJson()
			.isLenientlyEqualTo("{\"episodes\": [{\"episode\": 1}, {\"episode\": 2, \"upstreamMissing\": true,"
					+ " \"watched\": true}, {\"episode\": 3}]}");
		unwatch(alice, dropped);
		assertThat(watch(alice, dropped)).as("cannot be newly watched")
			.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/episode-unavailable");
	}

	// --- R10 / R11 ---------------------------------------------------------------------------------

	@Test
	void removingAndReAddingKeepsProgress() throws Exception {
		String show = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(21)), ep(1, 2, TODAY.minusDays(14)),
				ep(1, 3, TODAY.minusDays(7)));
		addToLibrary(alice, show, "WATCHING");
		watchUpTo(alice, episodeId(show, 1, 2));
		String before = progress(alice, show).getResponse().getContentAsString();

		deleteFromLibrary(alice, show);
		assertThat(progress(alice, show)).hasStatus(HttpStatus.NOT_FOUND);
		addToLibrary(alice, show, "WATCHING");

		assertThat(progress(alice, show)).bodyJson().isEqualTo(before);
	}

	@Test
	void purgingDeletesOnlyThisUsersHistoryForThatShow() {
		String show = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(14)), ep(1, 2, TODAY.minusDays(7)));
		String other = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(14)));
		store(other);
		addToLibrary(alice, show, "WATCHING");
		watchSeason(alice, show, 1);
		unwatch(alice, episodeId(show, 1, 1));
		watch(alice, episodeId(other, 1, 1));
		watch(bob, episodeId(show, 1, 1));

		assertThat(send("POST", alice, "/api/v1/library/" + show + "/purge",
				"{\"confirmName\": \"Show " + show.substring(5) + "\"}"))
			.hasStatus(HttpStatus.NO_CONTENT);

		assertThat(allWatchRows(alice)).as("active and soft-deleted rows of the show are gone").isEqualTo(1);
		assertThat(operations(alice)).as("only the other show's operation is left").isEqualTo(1);
		assertThat(activeWatches(bob)).isEqualTo(1);
		assertThat(operations(bob)).isEqualTo(1);
	}

	@Test
	void deletingAnAccountDeletesItsTrackingHistory() {
		String show = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(14)), ep(1, 2, TODAY.minusDays(7)));
		watchSeason(alice, show, 1);
		unwatch(alice, episodeId(show, 1, 1));
		watch(bob, episodeId(show, 1, 1));

		assertThat(mvc.delete()
			.uri("/api/v1/me")
			.cookie(alice.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"password\": \"" + app.watchmark.auth.AuthTestClient.PASSWORD + "\"}")
			.with(alice.client().csrf())
			.with(alice.client().fromClient())).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(allWatchRows(alice)).isZero();
		assertThat(operations(alice)).isZero();
		assertThat(activeWatches(bob)).isEqualTo(1);
	}

	// --- Helpers -----------------------------------------------------------------------------------

	private void deleteFromLibrary(User user, String showRef) {
		assertThat(send("DELETE", user, "/api/v1/library/" + showRef, null)).hasStatus(HttpStatus.NO_CONTENT);
	}

	private MvcTestResult send(String method, User user, String uri, String json) {
		var request = switch (method) {
			case "PUT" -> mvc.put().uri(uri);
			case "POST" -> mvc.post().uri(uri);
			case "DELETE" -> mvc.delete().uri(uri);
			default -> throw new IllegalArgumentException(method);
		};
		request = request.cookie(user.session()).with(user.client().csrf()).with(user.client().fromClient());
		if (json != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return request.exchange();
	}

}
