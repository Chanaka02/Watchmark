package app.watchmark.calendar;

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
 * The calendar: dated episodes in a range, undated ones listed separately and
 * never given a date, today in the user's time zone, R1 and R10.
 */
class CalendarApiTests extends TrackingTestSupport {

	User alice;

	User bob;

	@BeforeEach
	void setUp() {
		alice = newUser();
		bob = newUser();
	}

	@Test
	void listsDatedEpisodesInTheRangeWithWatchedState() {
		String show = publish(ShowStatus.RETURNING, ep(0, 1, TODAY.plusDays(2)), ep(1, 1, TODAY.minusDays(7)),
				ep(1, 2, TODAY), ep(1, 3, TODAY.plusDays(7)), ep(1, 4, TODAY.plusDays(40)));
		addToLibrary(alice, show, "WATCHING");
		watch(alice, episodeId(show, 1, 1));

		MvcTestResult result = calendar(alice, "from=2026-10-26&to=2026-12-06");

		assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"today": "2026-11-02", "from": "2026-10-26", "to": "2026-12-06",
				 "episodes": [
				   {"show": {"ref": "%s"}, "episode": {"season": 1, "episode": 1, "airDate": "2026-10-26",
				    "runtimeMinutes": 30}, "watched": true},
				   {"episode": {"season": 1, "episode": 2, "airDate": "2026-11-02"}, "watched": false},
				   {"episode": {"season": 0, "episode": 1, "airDate": "2026-11-04"}},
				   {"episode": {"season": 1, "episode": 3, "airDate": "2026-11-09"}}],
				 "unscheduled": []}
				""".formatted(show));
		List<String> dates = read(result, "$.episodes[*].episode.airDate");
		assertThat(dates).as("+40 days is outside the range").hasSize(4);
	}

	@Test
	void undatedEpisodesAreListedSeparatelyAndNeverGivenADate() {
		String show = publish(ShowStatus.RETURNING, ep(1, 1, null), ep(1, 2, TODAY.minusDays(3)), ep(1, 3, null),
				ep(1, 4, null), ep(2, 1, TODAY.plusDays(60)));
		String watchedUndated = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(9)), ep(1, 2, null));
		addToLibrary(alice, show, "WATCHING");
		addToLibrary(alice, watchedUndated, "WATCHING");

		MvcTestResult result = calendar(alice, "");

		assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"episodes": [],
				 "unscheduled": [
				   {"show": {"ref": "%1$s"}, "episodes": [{"season": 1, "episode": 3}, {"season": 1, "episode": 4}]},
				   {"show": {"ref": "%2$s"}, "episodes": [{"season": 1, "episode": 2}]}]}
				""".formatted(show, watchedUndated));
		List<?> unscheduledDates = read(result, "$.unscheduled[*].episodes[*].airDate");
		assertThat(unscheduledDates).as("no date field at all, estimated or otherwise").isEmpty();
	}

	@Test
	void evidentlyAiredOrWatchedUndatedEpisodesAreNotUnscheduled() {
		// Episode 1 has no date but episode 2 aired, so it aired too (R5): not "coming".
		String show = publish(ShowStatus.ENDED, ep(1, 1, null), ep(1, 2, TODAY.minusDays(3)), ep(1, 3, null));
		addToLibrary(alice, show, "WATCHING");
		// Episode 3 has no date and nothing later aired; once watched it is no longer "coming" either.
		jdbc.update("UPDATE episodes SET air_date = ? WHERE id = ?", java.sql.Date.valueOf(TODAY.minusDays(1)),
				episodeId(show, 1, 3));
		watch(alice, episodeId(show, 1, 3));
		jdbc.update("UPDATE episodes SET air_date = NULL WHERE id = ?", episodeId(show, 1, 3));

		assertThat(calendar(alice, "")).bodyJson().isLenientlyEqualTo("{\"unscheduled\": []}");
	}

	@Test
	void defaultRangeStartsAtTheUsersToday() {
		String show = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(1)), ep(1, 2, TODAY.plusDays(26)),
				ep(1, 3, TODAY.plusDays(27)));
		addToLibrary(alice, show, "WATCHING");
		// 06:00 UTC on 2 November is still 1 November in Honolulu.
		setTimeZone(alice, "Pacific/Honolulu");
		clock.set(NOW.minus(Duration.ofHours(4)));

		MvcTestResult result = calendar(alice, "");

		assertThat(result).bodyJson()
			.isLenientlyEqualTo("{\"today\": \"2026-11-01\", \"from\": \"2026-11-01\", \"to\": \"2026-11-28\"}");
		List<String> dates = read(result, "$.episodes[*].episode.airDate");
		assertThat(dates).containsExactly("2026-11-01", "2026-11-28");
	}

	@Test
	void droppedRemovedAndMissingEpisodesStayOff() {
		String dropped = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.plusDays(1)), ep(1, 2, null));
		String removed = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.plusDays(1)), ep(1, 2, null));
		String onHold = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.plusDays(1)), ep(1, 2, TODAY.plusDays(2)));
		addToLibrary(alice, dropped, "DROPPED");
		addToLibrary(alice, removed, "WATCHING");
		removeFromLibrary(alice, removed);
		addToLibrary(alice, onHold, "ON_HOLD");
		publishAs(onHold.substring(5), ShowStatus.RETURNING, List.of(ep(1, 1, TODAY.plusDays(1))));
		store(onHold);

		MvcTestResult result = calendar(alice, "");

		List<String> refs = read(result, "$.episodes[*].show.ref");
		assertThat(refs).as("on hold still follows the show; episode 2 was dropped upstream").containsExactly(onHold);
		assertThat(result).bodyJson().isLenientlyEqualTo("{\"unscheduled\": []}");
	}

	@Test
	void invalidRangesAreRejected() {
		for (String query : List.of("from=2026-11-10&to=2026-11-01", "from=2026-11-01&to=2027-01-02", "from=11/01/2026",
				"to=2026-13-01")) {
			assertThat(calendar(alice, query)).as(query)
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.type")
				.isEqualTo("/problems/invalid-range");
		}
		assertThat(calendar(alice, "from=2026-11-01&to=2027-01-01")).as("62 days inclusive").hasStatusOk();
		assertThat(calendar(alice, "from=2026-11-05")).as("to defaults to from + 27 days")
			.bodyJson()
			.isLenientlyEqualTo("{\"from\": \"2026-11-05\", \"to\": \"2026-12-02\"}");
	}

	@Test
	void theCalendarIsPrivate() {
		String show = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.plusDays(1)), ep(1, 2, null));
		addToLibrary(alice, show, "WATCHING");

		assertThat(calendar(bob, "")).bodyJson().isLenientlyEqualTo("{\"episodes\": [], \"unscheduled\": []}");
		assertThat(mvc.get().uri("/api/v1/me/calendar")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private MvcTestResult calendar(User user, String query) {
		return get(user, "/api/v1/me/calendar" + (query.isEmpty() ? "" : "?" + query));
	}

}
