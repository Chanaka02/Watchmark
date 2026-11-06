package app.watchmark.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import app.watchmark.catalog.ShowStatus;
import app.watchmark.tracking.TrackingTestSupport;

/**
 * Statistics verified against a hand-computed fixture. Today is 2026-11-02 in
 * the user's zone (Asia/Colombo, UTC+5:30).
 *
 * <pre>
 * when (UTC)        what                                   runtime
 * 2026-09-23 10:00  Comedy E1                              25
 * 2026-10-23 10:00  Drama season: E1, E2, E3               50, 50, unknown
 * 2026-10-30 10:00  rewatch Drama, E1 again (cycle 2)      50
 * 2026-11-01 10:00  Plain E1 (no genres; show removed later) 40
 *                   Comedy E2, then unwatched (not counted)
 * 2026-11-02 10:00  Comedy E2                              25
 * </pre>
 *
 * Drama has the genres Drama and Mystery, Comedy has Comedy, Plain has none.
 * A fourth show is in the library as Completed without any viewings.
 */
class StatsApiTests extends TrackingTestSupport {

	User alice;

	String drama;

	String comedy;

	String plain;

	@BeforeEach
	void setUp() {
		alice = newUser();
		drama = publishWithGenres(ShowStatus.ENDED, List.of("Drama", "Mystery"), ep(1, 1, TODAY.minusDays(100), 50),
				ep(1, 2, TODAY.minusDays(93), 50), ep(1, 3, TODAY.minusDays(86), null));
		comedy = publishWithGenres(ShowStatus.RETURNING, List.of("Comedy"), ep(1, 1, TODAY.minusDays(50), 25),
				ep(1, 2, TODAY.minusDays(43), 25));
		plain = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(10), 40));
		String finished = publish(ShowStatus.ENDED, ep(1, 1, TODAY.minusDays(300)));
		addToLibrary(alice, drama, "WATCHING");
		addToLibrary(alice, comedy, "WATCHING");
		addToLibrary(alice, plain, "WATCHING");
		addToLibrary(alice, finished, "COMPLETED");

		at(NOW.minus(Duration.ofDays(40)));
		watch(alice, episodeId(comedy, 1, 1));
		at(NOW.minus(Duration.ofDays(10)));
		watchSeason(alice, drama, 1);
		at(NOW.minus(Duration.ofDays(3)));
		rewatch(alice, drama);
		watch(alice, episodeId(drama, 1, 1));
		at(NOW.minus(Duration.ofDays(1)));
		watch(alice, episodeId(plain, 1, 1));
		watch(alice, episodeId(comedy, 1, 2));
		unwatch(alice, episodeId(comedy, 1, 2));
		at(NOW);
		watch(alice, episodeId(comedy, 1, 2));
		removeFromLibrary(alice, plain);
	}

	@Test
	void allTimeTotalsCountRewatchesAndRemovedShowsButNotUnwatchedRows() {
		assertThat(stats("all")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"range": "all", "from": null, "to": "2026-11-02",
				 "uniqueEpisodes": 6, "totalWatches": 7, "minutesWatched": 240, "watchesWithUnknownRuntime": 1,
				 "showsWatched": 3, "library": {"watching": 2, "completed": 1}}
				""");
	}

	@Test
	void topShowsAndGenres() {
		MvcTestResult result = stats("all");

		assertThat(result).bodyJson().isLenientlyEqualTo("""
				{"topShows": [
				   {"show": {"ref": "%s"}, "watches": 4, "minutes": 150},
				   {"show": {"ref": "%s"}, "watches": 2, "minutes": 50},
				   {"show": {"ref": "%s"}, "watches": 1, "minutes": 40}],
				 "topGenres": [
				   {"name": "Drama", "watches": 4, "minutes": 150},
				   {"name": "Mystery", "watches": 4, "minutes": 150},
				   {"name": "Comedy", "watches": 2, "minutes": 50}]}
				""".formatted(drama, comedy, plain));
		List<?> genres = read(result, "$.topGenres");
		assertThat(genres).as("Plain has no genres and is not counted as one").hasSize(3);
	}

	@Test
	void allTimeActivityIsMonthlyFromTheFirstViewing() {
		assertThat(stats("all")).bodyJson().isLenientlyEqualTo("""
				{"activity": {"unit": "MONTH", "points": [
				   {"period": "2026-09", "watches": 1, "minutes": 25},
				   {"period": "2026-10", "watches": 4, "minutes": 150},
				   {"period": "2026-11", "watches": 2, "minutes": 65}]}}
				""");
	}

	@Test
	void lastSevenDays() {
		MvcTestResult result = stats("week");

		assertThat(result).bodyJson().isLenientlyEqualTo("""
				{"range": "week", "from": "2026-10-27", "to": "2026-11-02",
				 "uniqueEpisodes": 3, "totalWatches": 3, "minutesWatched": 115, "watchesWithUnknownRuntime": 0,
				 "showsWatched": 3}
				""");
		List<String> days = read(result, "$.activity.points[*].period");
		assertThat(days).containsExactly("2026-10-27", "2026-10-28", "2026-10-29", "2026-10-30", "2026-10-31",
				"2026-11-01", "2026-11-02");
		List<Integer> watches = read(result, "$.activity.points[*].watches");
		assertThat(watches).as("zero days included").containsExactly(0, 0, 0, 1, 0, 1, 1);
	}

	@Test
	void lastThirtyDaysIsTheDefault() {
		MvcTestResult result = stats(null);

		assertThat(result).bodyJson().isLenientlyEqualTo("""
				{"range": "month", "from": "2026-10-04", "to": "2026-11-02",
				 "uniqueEpisodes": 5, "totalWatches": 6, "minutesWatched": 215, "watchesWithUnknownRuntime": 1,
				 "activity": {"unit": "DAY"}}
				""");
		List<?> points = read(result, "$.activity.points");
		assertThat(points).hasSize(30);
	}

	@Test
	void rangesFollowTheUsersCalendarDays() {
		User bob = newUser();
		String show = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(30), 30));
		addToLibrary(bob, show, "WATCHING");
		// 20:00 UTC on 26 October: already 27 October (01:30) in Colombo, still 26 October in Los Angeles.
		at(Instant.parse("2026-10-26T20:00:00Z"));
		watch(bob, episodeId(show, 1, 1));
		at(NOW);

		assertThat(stats(bob, "week")).bodyJson().isLenientlyEqualTo("{\"totalWatches\": 1}");
		setTimeZone(bob, "America/Los_Angeles");
		assertThat(stats(bob, "week")).bodyJson()
			.isLenientlyEqualTo("{\"from\": \"2026-10-27\", \"to\": \"2026-11-02\", \"totalWatches\": 0}");
	}

	@Test
	void aNewUserGetsZerosNotPlaceholders() {
		User bob = newUser();

		assertThat(stats(bob, "all")).bodyJson().isEqualTo("""
				{"range": "all", "from": null, "to": "2026-11-02",
				 "uniqueEpisodes": 0, "totalWatches": 0, "minutesWatched": 0, "watchesWithUnknownRuntime": 0,
				 "showsWatched": 0, "library": {"watching": 0, "completed": 0},
				 "topShows": [], "topGenres": [], "activity": {"unit": "MONTH", "points": []}}
				""");
		List<Integer> week = read(stats(bob, "week"), "$.activity.points[*].watches");
		assertThat(week).containsExactly(0, 0, 0, 0, 0, 0, 0);
	}

	@Test
	void invalidRangesAreRejectedAndStatsArePrivate() {
		assertThat(stats("year")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/invalid-range");
		assertThat(mvc.get().uri("/api/v1/me/stats")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// --- Helpers -----------------------------------------------------------------------------------

	private void at(Instant instant) {
		clock.set(instant);
	}

	private MvcTestResult stats(String range) {
		return stats(alice, range);
	}

	private MvcTestResult stats(User user, String range) {
		return get(user, "/api/v1/me/stats" + (range == null ? "" : "?range=" + range));
	}

}
