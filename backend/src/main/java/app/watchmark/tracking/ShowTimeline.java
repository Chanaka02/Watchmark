package app.watchmark.tracking;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A show's stored episodes as of the user's today: which episodes may be marked
 * watched (rule R5), and progress and states for a set of watched episodes (R6,
 * R7). Pure logic, no database; see docs/tracking-rules.md.
 */
public final class ShowTimeline {

	enum Eligibility {

		ELIGIBLE,
		/** Future date, or unknown date without evidence that it aired. */
		NOT_AIRED,
		/** Unknown here, or no longer listed by the provider (R8). */
		UNAVAILABLE

	}

	/** See docs/tracking-rules.md, R7. */
	public enum State {

		NOT_STARTED, IN_PROGRESS, CAUGHT_UP, COMPLETE

	}

	/** @param active {@code false} when the provider no longer lists the episode (R8) */
	record TimelineEpisode(long id, int season, int number, LocalDate airDate, boolean active) {

		boolean special() {
			return season == 0;
		}

	}

	/**
	 * @param watched watched episodes
	 * @param eligible episodes that are eligible or already watched (the denominator)
	 * @param total all episodes
	 */
	record Counts(int watched, int eligible, int total) {

		static final Counts EMPTY = new Counts(0, 0, 0);

		/** Watched share of {@code eligible}, rounded to one decimal; 0 when nothing is eligible. */
		double percent() {
			return eligible == 0 ? 0 : Math.round(watched * 1000.0 / eligible) / 10.0;
		}

		State state() {
			if (watched == 0) {
				return State.NOT_STARTED;
			}
			if (watched == total) {
				return State.COMPLETE;
			}
			return watched == eligible ? State.CAUGHT_UP : State.IN_PROGRESS;
		}

		Counts plus(boolean isWatched, boolean isEligible) {
			return new Counts(watched + (isWatched ? 1 : 0), eligible + (isWatched || isEligible ? 1 : 0), total + 1);
		}

	}

	/**
	 * @param overall regular episodes only (specials never count towards the show)
	 * @param seasons per season number, specials (0) included
	 * @param nextEpisodeId the episode to watch next, or {@code null} when caught up
	 */
	record Progress(Counts overall, SortedMap<Integer, Counts> seasons, Long nextEpisodeId) {

		Counts season(int number) {
			return seasons.getOrDefault(number, Counts.EMPTY);
		}

	}

	private static final Comparator<TimelineEpisode> ORDER = Comparator.comparingInt(TimelineEpisode::season)
		.thenComparingInt(TimelineEpisode::number)
		.thenComparingLong(TimelineEpisode::id);

	private final List<TimelineEpisode> episodes;

	private final Map<Long, TimelineEpisode> byId = new HashMap<>();

	private final Map<Long, Eligibility> eligibility = new HashMap<>();

	ShowTimeline(List<TimelineEpisode> episodes, LocalDate today) {
		this.episodes = episodes.stream().sorted(ORDER).toList();
		for (TimelineEpisode episode : this.episodes) {
			byId.put(episode.id(), episode);
		}
		classify(this.episodes.stream().filter(e -> !e.special()).toList(), today);
		classify(this.episodes.stream().filter(TimelineEpisode::special).toList(), today);
	}

	/**
	 * Walks one sequence backwards, remembering whether a later active episode
	 * has aired: that is evidence an episode with an unknown date aired too.
	 */
	private void classify(List<TimelineEpisode> sequence, LocalDate today) {
		boolean laterAired = false;
		for (int i = sequence.size() - 1; i >= 0; i--) {
			TimelineEpisode episode = sequence.get(i);
			if (!episode.active()) {
				eligibility.put(episode.id(), Eligibility.UNAVAILABLE);
				continue;
			}
			boolean aired = episode.airDate() != null && !episode.airDate().isAfter(today);
			boolean eligible = aired || (episode.airDate() == null && laterAired);
			eligibility.put(episode.id(), eligible ? Eligibility.ELIGIBLE : Eligibility.NOT_AIRED);
			laterAired |= aired;
		}
	}

	Optional<TimelineEpisode> episode(long id) {
		return Optional.ofNullable(byId.get(id));
	}

	Eligibility eligibility(long id) {
		return eligibility.getOrDefault(id, Eligibility.UNAVAILABLE);
	}

	boolean isEligible(long id) {
		return eligibility(id) == Eligibility.ELIGIBLE;
	}

	/** Active episodes of one season, in order. */
	List<TimelineEpisode> season(int number) {
		return episodes.stream().filter(e -> e.active() && e.season() == number).toList();
	}

	/**
	 * Active episodes up to and including the given one: regular episodes across
	 * earlier seasons for a regular episode, specials for a special.
	 */
	List<TimelineEpisode> upTo(long id) {
		TimelineEpisode target = byId.get(id);
		if (target == null) {
			return List.of();
		}
		return episodes.stream()
			.filter(e -> e.active() && e.special() == target.special() && ORDER.compare(e, target) <= 0)
			.toList();
	}

	Progress progress(Set<Long> watched) {
		Counts overall = Counts.EMPTY;
		SortedMap<Integer, Counts> seasons = new TreeMap<>();
		List<TimelineEpisode> regular = new ArrayList<>();
		for (TimelineEpisode episode : episodes) {
			if (!episode.active()) {
				continue;
			}
			boolean isWatched = watched.contains(episode.id());
			boolean isEligible = isEligible(episode.id());
			seasons.merge(episode.season(), Counts.EMPTY.plus(isWatched, isEligible),
					(a, b) -> a.plus(isWatched, isEligible));
			if (!episode.special()) {
				overall = overall.plus(isWatched, isEligible);
				regular.add(episode);
			}
		}
		return new Progress(overall, Collections.unmodifiableSortedMap(seasons), next(regular, watched));
	}

	/** The first eligible, unwatched episode after the furthest watched one. */
	private Long next(List<TimelineEpisode> regular, Set<Long> watched) {
		int furthest = -1;
		for (int i = 0; i < regular.size(); i++) {
			if (watched.contains(regular.get(i).id())) {
				furthest = i;
			}
		}
		for (int i = furthest + 1; i < regular.size(); i++) {
			long id = regular.get(i).id();
			if (isEligible(id) && !watched.contains(id)) {
				return id;
			}
		}
		return null;
	}

}
