package app.watchmark.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import app.watchmark.catalog.ShowStatus;
import app.watchmark.tracking.TrackingTestSupport;

/**
 * The dashboard shows only the user's real data: no placeholders, nothing from
 * removed shows (R10) and nothing from other users (R1).
 */
class DashboardApiTests extends TrackingTestSupport {

	User alice;

	User bob;

	@BeforeEach
	void setUp() {
		alice = newUser();
		bob = newUser();
	}

	@Test
	void aNewUserSeesAnEmptyDashboard() {
		assertThat(dashboard(alice)).hasStatusOk().bodyJson().isEqualTo("""
				{"today": "2026-11-02", "continueWatching": [], "upcoming": [], "recentlyWatched": [],
				 "recentlyAdded": []}
				""");
	}

	@Test
	void continueWatchingListsWatchingShowsWithAnAiredNextEpisode() {
		String started = threeEpisodeShow();
		String recent = threeEpisodeShow();
		String caughtUp = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(7)), ep(1, 2, TODAY.plusDays(7)));
		String untouched = threeEpisodeShow();
		String planned = threeEpisodeShow();
		String onHold = threeEpisodeShow();
		String removed = threeEpisodeShow();
		for (String show : List.of(started, recent, caughtUp, untouched, removed)) {
			addToLibrary(alice, show, "WATCHING");
		}
		addToLibrary(alice, planned, "PLAN_TO_WATCH");
		addToLibrary(alice, onHold, "ON_HOLD");
		watch(alice, episodeId(started, 1, 1));
		watch(alice, episodeId(caughtUp, 1, 1));
		watch(alice, episodeId(onHold, 1, 1));
		watch(alice, episodeId(removed, 1, 1));
		removeFromLibrary(alice, removed);
		clock.advance(Duration.ofHours(1));
		watch(alice, episodeId(recent, 1, 1));

		MvcTestResult result = dashboard(alice);

		List<String> refs = read(result, "$.continueWatching[*].show.ref");
		assertThat(refs).as("most recently watched first; never-watched shows last").containsExactly(recent, started,
				untouched);
		assertThat(result).bodyJson().isLenientlyEqualTo("""
				{"continueWatching": [
				   {"cycle": 1, "lastWatchedAt": "2026-11-02T11:00:00Z",
				    "progress": {"watched": 1, "eligible": 2, "total": 3, "percent": 50.0, "state": "IN_PROGRESS"},
				    "nextEpisode": {"season": 1, "episode": 2, "name": "Episode 2"}},
				   {"lastWatchedAt": "2026-11-02T10:00:00Z"},
				   {"lastWatchedAt": null, "nextEpisode": {"episode": 1}}]}
				""");
	}

	@Test
	void upcomingListsTheNextTwoWeeksOfEpisodesInDateOrder() {
		String airing = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(7)), ep(1, 2, TODAY),
				ep(1, 3, TODAY.plusDays(14)), ep(1, 4, TODAY.plusDays(15)));
		String other = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.plusDays(3)), ep(1, 2, null));
		String watchedToday = publish(ShowStatus.RETURNING, ep(1, 1, TODAY), ep(1, 2, TODAY.plusDays(1)));
		String dropped = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.plusDays(1)));
		String removed = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.plusDays(1)));
		addToLibrary(alice, airing, "WATCHING");
		addToLibrary(alice, other, "PLAN_TO_WATCH");
		addToLibrary(alice, watchedToday, "WATCHING");
		addToLibrary(alice, dropped, "DROPPED");
		addToLibrary(alice, removed, "WATCHING");
		removeFromLibrary(alice, removed);
		watch(alice, episodeId(watchedToday, 1, 1));

		MvcTestResult result = dashboard(alice);

		List<String> episodes = read(result, "$.upcoming[*].episode.airDate");
		assertThat(episodes).as("today, +1, +3 and +14; not +15, not undated, not dropped or removed shows")
			.containsExactly("2026-11-02", "2026-11-03", "2026-11-05", "2026-11-16");
		List<String> shows = read(result, "$.upcoming[*].show.ref");
		assertThat(shows).containsExactly(airing, watchedToday, other, airing);
	}

	@Test
	void upcomingUsesTheUsersToday() {
		String show = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(7)), ep(1, 2, TODAY.minusDays(1)),
				ep(1, 3, TODAY.plusDays(14)));
		addToLibrary(alice, show, "WATCHING");
		// 06:00 UTC on 2 November is still 1 November (20:00) in Honolulu: yesterday's episode is "today" there.
		setTimeZone(alice, "Pacific/Honolulu");
		clock.set(NOW.minus(Duration.ofHours(4)));

		MvcTestResult result = dashboard(alice);

		assertThat(result).bodyJson().extractingPath("$.today").isEqualTo("2026-11-01");
		List<String> dates = read(result, "$.upcoming[*].episode.airDate");
		assertThat(dates).containsExactly("2026-11-01");
	}

	@Test
	void recentlyWatchedSkipsRemovedShowsAndIsCapped() {
		String kept = publish(ShowStatus.RETURNING, episodes(12));
		String removed = threeEpisodeShow();
		addToLibrary(alice, kept, "WATCHING");
		addToLibrary(alice, removed, "WATCHING");
		watchSeason(alice, removed, 1);
		removeFromLibrary(alice, removed);
		for (int n = 1; n <= 12; n++) {
			clock.advance(Duration.ofMinutes(1));
			watch(alice, episodeId(kept, 1, n));
		}

		MvcTestResult result = dashboard(alice);

		List<Integer> numbers = read(result, "$.recentlyWatched[*].episode.episode");
		assertThat(numbers).containsExactly(12, 11, 10, 9, 8, 7, 6, 5, 4, 3);
		List<String> refs = read(result, "$.recentlyWatched[*].show.ref");
		assertThat(refs).containsOnly(kept);
	}

	@Test
	void recentlyAddedIsNewestFirstWithoutRemovedShows() {
		String removed = threeEpisodeShow();
		addToLibrary(alice, removed, "WATCHING");
		String[] shows = new String[7];
		for (int i = 0; i < shows.length; i++) {
			clock.advance(Duration.ofMinutes(1));
			shows[i] = threeEpisodeShow();
			addToLibrary(alice, shows[i], "PLAN_TO_WATCH");
		}
		removeFromLibrary(alice, removed);

		List<String> refs = read(dashboard(alice), "$.recentlyAdded[*].show.ref");

		assertThat(refs).containsExactly(shows[6], shows[5], shows[4], shows[3], shows[2], shows[1]);
	}

	@Test
	void theDashboardIsPrivate() {
		String show = threeEpisodeShow();
		addToLibrary(alice, show, "WATCHING");
		watch(alice, episodeId(show, 1, 1));

		assertThat(dashboard(bob)).bodyJson()
			.isLenientlyEqualTo("{\"continueWatching\": [], \"recentlyWatched\": [], \"recentlyAdded\": []}");
		assertThat(mvc.get().uri("/api/v1/me/dashboard")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// --- Helpers -----------------------------------------------------------------------------------

	/** Episode 3 airs next week, so watching 1 leaves 2 as the next aired episode. */
	private String threeEpisodeShow() {
		return publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(14)), ep(1, 2, TODAY.minusDays(7)),
				ep(1, 3, TODAY.plusDays(7)));
	}

	private static Ep[] episodes(int count) {
		Ep[] episodes = new Ep[count];
		for (int i = 0; i < count; i++) {
			episodes[i] = ep(1, i + 1, TODAY.minusDays(100 - i));
		}
		return episodes;
	}

	private MvcTestResult dashboard(User user) {
		return get(user, "/api/v1/me/dashboard");
	}

}
