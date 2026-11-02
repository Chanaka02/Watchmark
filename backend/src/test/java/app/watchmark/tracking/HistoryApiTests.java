package app.watchmark.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import app.watchmark.catalog.ShowStatus;

/** Viewing history: newest first, every cycle, removed shows kept (R10), stable cursor paging, R1. */
class HistoryApiTests extends TrackingTestSupport {

	User alice;

	User bob;

	String show;

	@BeforeEach
	void setUp() {
		alice = newUser();
		bob = newUser();
		show = publish(ShowStatus.ENDED, ep(1, 1, TODAY.minusDays(30)), ep(1, 2, TODAY.minusDays(23)),
				ep(1, 3, TODAY.minusDays(16)));
		addToLibrary(alice, show, "WATCHING");
	}

	@Test
	void historyListsViewingsNewestFirst() {
		watch(alice, episodeId(show, 1, 1));
		clock.advance(Duration.ofHours(1));
		watch(alice, episodeId(show, 1, 2));

		assertThat(history(alice, "")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"items": [
				   {"watchedAt": "2026-11-02T11:00:00Z", "cycle": 1, "inLibrary": true,
				    "show": {"ref": "%1$s", "name": "Show %2$s"},
				    "episode": {"season": 1, "episode": 2, "name": "Episode 2"}},
				   {"watchedAt": "2026-11-02T10:00:00Z", "episode": {"episode": 1}}],
				 "nextCursor": null}
				""".formatted(show, show.substring(5)));
	}

	@Test
	void historyIncludesRewatchesAndRemovedShowsButNotUnwatchedEpisodes() {
		watchSeason(alice, show, 1);
		clock.advance(Duration.ofMinutes(5));
		unwatch(alice, episodeId(show, 1, 3));
		watch(alice, episodeId(show, 1, 3));
		clock.advance(Duration.ofMinutes(5));
		rewatch(alice, show);
		watch(alice, episodeId(show, 1, 1));
		removeFromLibrary(alice, show);

		MvcTestResult result = history(alice, "");

		List<Integer> cycles = read(result, "$.items[*].cycle");
		assertThat(cycles).containsExactly(2, 1, 1, 1);
		List<Boolean> inLibrary = read(result, "$.items[*].inLibrary");
		assertThat(inLibrary).as("a removed show stays in history (R10)").containsOnly(false);
		List<Integer> episodes = read(result, "$.items[*].episode.episode");
		// Cycle 2's episode 1, the re-watched episode 3, then the season's rows (same time: newest id first).
		assertThat(episodes).as("the unwatched row is gone").containsExactly(1, 3, 2, 1);
	}

	@Test
	void cursorPagesAreStableAcrossRowsWithTheSameTime() {
		String other = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(9)), ep(1, 2, TODAY.minusDays(2)));
		addToLibrary(alice, other, "WATCHING");
		watchSeason(alice, show, 1);
		watchSeason(alice, other, 1);

		List<Long> seen = new ArrayList<>();
		String cursor = "";
		int pages = 0;
		do {
			MvcTestResult page = history(alice, "size=2" + cursor);
			assertThat(page).hasStatusOk();
			List<Number> ids = read(page, "$.items[*].watchId");
			ids.forEach(id -> seen.add(id.longValue()));
			String next = read(page, "$.nextCursor");
			cursor = next == null ? null : "&cursor=" + next;
			pages++;
		}
		while (cursor != null && pages < 10);

		assertThat(pages).isEqualTo(3);
		assertThat(seen).hasSize(5).doesNotHaveDuplicates();
		assertThat(seen).as("same watched_at: newest id first").isSortedAccordingTo((a, b) -> Long.compare(b, a));
	}

	@Test
	void invalidCursorsAndSizesAreRejected() {
		assertThat(history(alice, "cursor=garbage")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/invalid-cursor");
		assertThat(history(alice, "size=0")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(history(alice, "size=101")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void historyIsPrivate() {
		watch(alice, episodeId(show, 1, 1));

		assertThat(history(bob, "")).bodyJson().isLenientlyEqualTo("{\"items\": [], \"nextCursor\": null}");
		assertThat(mvc.get().uri("/api/v1/me/history")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private MvcTestResult history(User user, String query) {
		return get(user, "/api/v1/me/history" + (query.isEmpty() ? "" : "?" + query));
	}

}
