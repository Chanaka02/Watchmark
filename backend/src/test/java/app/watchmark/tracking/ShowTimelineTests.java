package app.watchmark.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import app.watchmark.tracking.ShowTimeline.Counts;
import app.watchmark.tracking.ShowTimeline.Eligibility;
import app.watchmark.tracking.ShowTimeline.Progress;
import app.watchmark.tracking.ShowTimeline.TimelineEpisode;

/** Rules R5 (eligibility), R6 (progress) and R7 (season states) without a database. */
class ShowTimelineTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

	private final List<TimelineEpisode> episodes = new ArrayList<>();

	private long nextId = 1;

	// --- R5: eligibility ---------------------------------------------------------------------------

	@Test
	void episodesAiringOnOrBeforeTodayAreEligible() {
		long yesterday = add(1, 1, TODAY.minusDays(1));
		long today = add(1, 2, TODAY);
		long tomorrow = add(1, 3, TODAY.plusDays(1));

		ShowTimeline timeline = timeline();

		assertThat(timeline.eligibility(yesterday)).isEqualTo(Eligibility.ELIGIBLE);
		assertThat(timeline.eligibility(today)).as("today counts as aired").isEqualTo(Eligibility.ELIGIBLE);
		assertThat(timeline.eligibility(tomorrow)).isEqualTo(Eligibility.NOT_AIRED);
	}

	@Test
	void unknownDateIsEligibleOnlyWhenALaterRegularEpisodeHasAired() {
		long gap = add(1, 1, null);
		add(1, 2, TODAY.minusDays(3));
		long unknownLast = add(1, 3, null);
		long unknownBeforeFuture = add(2, 1, null);
		add(2, 2, TODAY.plusDays(7));

		ShowTimeline timeline = timeline();

		assertThat(timeline.eligibility(gap)).as("a later episode aired, so this one did").isEqualTo(Eligibility.ELIGIBLE);
		assertThat(timeline.eligibility(unknownLast)).isEqualTo(Eligibility.NOT_AIRED);
		assertThat(timeline.eligibility(unknownBeforeFuture)).as("a later episode that has not aired is no evidence")
			.isEqualTo(Eligibility.NOT_AIRED);
	}

	@Test
	void laterSeasonsCountAsEvidenceForUnknownDates() {
		long unknown = add(1, 8, null);
		add(2, 1, TODAY.minusDays(30));

		assertThat(timeline().eligibility(unknown)).isEqualTo(Eligibility.ELIGIBLE);
	}

	@Test
	void specialsFollowTheSameRuleAmongSpecials() {
		long datedSpecial = add(0, 1, TODAY.minusDays(100));
		long unknownWithLaterSpecial = add(0, 2, null);
		add(0, 3, TODAY.minusDays(10));
		long unknownLastSpecial = add(0, 4, null);
		long futureSpecial = add(0, 5, TODAY.plusDays(1));
		add(1, 1, TODAY.minusDays(50));

		ShowTimeline timeline = timeline();

		assertThat(timeline.eligibility(datedSpecial)).isEqualTo(Eligibility.ELIGIBLE);
		assertThat(timeline.eligibility(unknownWithLaterSpecial)).isEqualTo(Eligibility.ELIGIBLE);
		assertThat(timeline.eligibility(unknownLastSpecial)).as("regular episodes are no evidence for a special")
			.isEqualTo(Eligibility.NOT_AIRED);
		assertThat(timeline.eligibility(futureSpecial)).isEqualTo(Eligibility.NOT_AIRED);
	}

	@Test
	void upstreamMissingEpisodesAreUnavailableAndAreNoEvidence() {
		long unknown = add(1, 1, null);
		long missing = addMissing(1, 2, TODAY.minusDays(5));

		ShowTimeline timeline = timeline();

		assertThat(timeline.eligibility(missing)).isEqualTo(Eligibility.UNAVAILABLE);
		assertThat(timeline.eligibility(unknown)).isEqualTo(Eligibility.NOT_AIRED);
		assertThat(timeline.eligibility(9999)).isEqualTo(Eligibility.UNAVAILABLE);
	}

	// --- R6: progress ------------------------------------------------------------------------------

	@Test
	void specialsAreExcludedFromTheShowPercentage() {
		long special = add(0, 1, TODAY.minusDays(10));
		long e1 = add(1, 1, TODAY.minusDays(9));
		add(1, 2, TODAY.minusDays(2));

		Progress progress = timeline().progress(Set.of(special, e1));

		assertThat(progress.overall()).isEqualTo(new Counts(1, 2, 2));
		assertThat(progress.overall().percent()).isEqualTo(50.0);
		assertThat(progress.season(0).watched()).as("specials still tracked per season").isEqualTo(1);
	}

	@Test
	void futureEpisodesAreNotInTheDenominatorUntilTheyAir() {
		long e1 = add(1, 1, TODAY.minusDays(7));
		add(1, 2, TODAY.plusDays(7));
		add(1, 3, null);

		Counts overall = timeline().progress(Set.of(e1)).overall();

		assertThat(overall).isEqualTo(new Counts(1, 1, 3));
		assertThat(overall.percent()).isEqualTo(100.0);
	}

	@Test
	void postponedWatchedEpisodesKeepCounting() {
		long e1 = add(1, 1, TODAY.minusDays(7));
		long postponed = add(1, 2, TODAY.plusDays(30));

		Counts overall = timeline().progress(Set.of(e1, postponed)).overall();

		assertThat(overall).as("watched, then moved to the future: progress does not drop")
			.isEqualTo(new Counts(2, 2, 2));
	}

	@Test
	void upstreamMissingEpisodesAreExcludedEvenWhenWatched() {
		long e1 = add(1, 1, TODAY.minusDays(7));
		long missing = addMissing(1, 2, TODAY.minusDays(1));
		add(1, 3, TODAY);

		Counts overall = timeline().progress(Set.of(e1, missing)).overall();

		assertThat(overall).isEqualTo(new Counts(1, 2, 2));
	}

	@Test
	void anEmptyShowReportsZeroPercent() {
		Progress progress = timeline().progress(Set.of());

		assertThat(progress.overall()).isEqualTo(new Counts(0, 0, 0));
		assertThat(progress.overall().percent()).isZero();
		assertThat(progress.overall().state()).isEqualTo(ShowTimeline.State.NOT_STARTED);
		assertThat(progress.seasons()).isEmpty();
		assertThat(progress.nextEpisodeId()).isNull();
	}

	@Test
	void percentIsRoundedToOneDecimal() {
		long e1 = add(1, 1, TODAY.minusDays(3));
		add(1, 2, TODAY.minusDays(2));
		add(1, 3, TODAY.minusDays(1));

		assertThat(timeline().progress(Set.of(e1)).overall().percent()).isEqualTo(33.3);
	}

	// --- R7: season states -------------------------------------------------------------------------

	@Test
	void seasonStates() {
		long complete1 = add(1, 1, TODAY.minusDays(20));
		long complete2 = add(1, 2, TODAY.minusDays(13));
		long caughtUp = add(2, 1, TODAY.minusDays(6));
		add(2, 2, TODAY.plusDays(1));
		long started = add(3, 1, TODAY.minusDays(3));
		add(3, 2, TODAY.minusDays(2));
		add(4, 1, TODAY.minusDays(1));
		add(5, 1, null);

		Progress progress = timeline().progress(Set.of(complete1, complete2, caughtUp, started));

		assertThat(progress.season(1).state()).isEqualTo(ShowTimeline.State.COMPLETE);
		assertThat(progress.season(2).state()).isEqualTo(ShowTimeline.State.CAUGHT_UP);
		assertThat(progress.season(3).state()).isEqualTo(ShowTimeline.State.IN_PROGRESS);
		assertThat(progress.season(4).state()).isEqualTo(ShowTimeline.State.NOT_STARTED);
		assertThat(progress.season(5).state()).as("nothing aired, nothing watched")
			.isEqualTo(ShowTimeline.State.NOT_STARTED);
		assertThat(progress.overall().state()).isEqualTo(ShowTimeline.State.IN_PROGRESS);
	}

	@Test
	void showIsCompleteOnlyWhenEveryRegularEpisodeIsWatched() {
		long e1 = add(1, 1, TODAY.minusDays(14));
		long e2 = add(1, 2, TODAY.minusDays(7));
		add(0, 1, TODAY.minusDays(1));

		Progress progress = timeline().progress(Set.of(e1, e2));

		assertThat(progress.overall().state()).as("an unwatched special does not block completion")
			.isEqualTo(ShowTimeline.State.COMPLETE);
		assertThat(progress.nextEpisodeId()).isNull();
	}

	@Test
	void caughtUpWhenEverythingAiredIsWatchedButMoreIsComing() {
		long e1 = add(1, 1, TODAY.minusDays(7));
		add(1, 2, TODAY.plusDays(7));

		assertThat(timeline().progress(Set.of(e1)).overall().state()).isEqualTo(ShowTimeline.State.CAUGHT_UP);
	}

	// --- Next episode and bulk selections ----------------------------------------------------------

	@Test
	void nextEpisodeFollowsTheFurthestWatchedOne() {
		add(1, 1, TODAY.minusDays(30));
		long e2 = add(1, 2, TODAY.minusDays(23));
		long e3 = add(1, 3, TODAY.minusDays(16));
		add(1, 4, TODAY.plusDays(5));

		ShowTimeline timeline = timeline();

		assertThat(timeline.progress(Set.of()).nextEpisodeId()).isEqualTo(1L);
		assertThat(timeline.progress(Set.of(e2)).nextEpisodeId()).as("a skipped episode is not 'next'").isEqualTo(e3);
		assertThat(timeline.progress(Set.of(e3)).nextEpisodeId()).as("the next one has not aired").isNull();
	}

	@Test
	void upToCoversEarlierRegularEpisodesAcrossSeasons() {
		long s1e1 = add(1, 1, TODAY.minusDays(30));
		long s1e2 = add(1, 2, TODAY.minusDays(23));
		long s2e1 = add(2, 1, TODAY.minusDays(16));
		add(2, 2, TODAY.minusDays(9));
		add(0, 1, TODAY.minusDays(40));

		assertThat(timeline().upTo(s2e1)).extracting(TimelineEpisode::id).containsExactly(s1e1, s1e2, s2e1);
	}

	@Test
	void upToOnASpecialCoversOnlySpecials() {
		long sp1 = add(0, 1, TODAY.minusDays(40));
		long sp2 = add(0, 2, TODAY.minusDays(20));
		add(1, 1, TODAY.minusDays(30));

		assertThat(timeline().upTo(sp2)).extracting(TimelineEpisode::id).containsExactly(sp1, sp2);
	}

	@Test
	void bulkSelectionsLeaveOutUpstreamMissingEpisodes() {
		long e1 = add(1, 1, TODAY.minusDays(30));
		addMissing(1, 2, TODAY.minusDays(23));
		long e3 = add(1, 3, TODAY.minusDays(16));

		ShowTimeline timeline = timeline();

		assertThat(timeline.upTo(e3)).extracting(TimelineEpisode::id).containsExactly(e1, e3);
		assertThat(timeline.season(1)).extracting(TimelineEpisode::id).containsExactly(e1, e3);
	}

	// --- Helpers -----------------------------------------------------------------------------------

	private long add(int season, int number, LocalDate airDate) {
		long id = nextId++;
		episodes.add(new TimelineEpisode(id, season, number, airDate, true));
		return id;
	}

	private long addMissing(int season, int number, LocalDate airDate) {
		long id = nextId++;
		episodes.add(new TimelineEpisode(id, season, number, airDate, false));
		return id;
	}

	private ShowTimeline timeline() {
		return new ShowTimeline(episodes, TODAY);
	}

}
