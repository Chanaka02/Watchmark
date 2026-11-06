package app.watchmark.tracking.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import app.watchmark.library.LibraryStatus;
import app.watchmark.library.dto.LibraryDtos.LibraryShow;
import app.watchmark.tracking.OperationType;
import app.watchmark.tracking.ShowTimeline.State;

/** Tracking and progress API contract (see docs/api.md). */
public final class TrackingDtos {

	private TrackingDtos() {
	}

	/**
	 * Result of marking one or more episodes watched.
	 *
	 * @param operationId the operation to undo, or {@code null} when nothing changed
	 * @param created episodes newly marked watched
	 * @param alreadyWatched skipped: already watched in the current cycle
	 * @param notEligible skipped: not aired yet (rule R5)
	 */
	public record WatchResult(UUID operationId, String showRef, int created, int alreadyWatched, int notEligible) {
	}

	/** @param operationId {@code null} when the episode was not watched in the current cycle */
	public record UnwatchResult(UUID operationId, String showRef, int removed) {
	}

	/** @param reverted watch rows removed (or, for an unwatch, restored) */
	public record UndoResult(UUID operationId, OperationType type, String showRef, int reverted) {
	}

	/**
	 * @param watched episodes watched in the current cycle
	 * @param eligible episodes eligible or already watched (the denominator, rule R6)
	 * @param total all episodes still listed by the provider
	 * @param percent {@code watched / eligible}, one decimal; 0 when nothing is eligible
	 */
	public record Counts(int watched, int eligible, int total, double percent, State state) {
	}

	public record SeasonSummary(int number, boolean special, int watched, int eligible, int total, double percent,
			State state) {
	}

	public record NextEpisode(long id, int season, int episode, String name, LocalDate airDate) {
	}

	/**
	 * Across every cycle, including episodes no longer listed upstream (R2, R8).
	 *
	 * @param uniqueEpisodes distinct episodes watched
	 * @param totalWatches viewings, each rewatch counted again
	 */
	public record History(long uniqueEpisodes, long totalWatches) {
	}

	/**
	 * @param overall regular episodes of the current cycle; specials never count
	 * @param nextEpisode {@code null} when caught up
	 * @param canRewatch the current cycle has at least one watch
	 */
	public record ShowProgress(String showRef, int cycle, LibraryStatus status, Counts overall, History history,
			List<SeasonSummary> seasons, NextEpisode nextEpisode, boolean canRewatch) {
	}

	/**
	 * @param watchedAt when watched in the current cycle, or {@code null}
	 * @param upstreamMissing no longer listed by the provider; only shown when watched (R8)
	 */
	public record EpisodeProgress(long id, String externalId, int episode, String name, LocalDate airDate,
			boolean eligible, boolean watched, Instant watchedAt, boolean upstreamMissing) {
	}

	public record SeasonProgress(String showRef, int cycle, int number, boolean special, int watched, int eligible,
			int total, double percent, State state, List<EpisodeProgress> episodes) {
	}

	/**
	 * Where a show stands, for overviews such as the dashboard.
	 *
	 * @param nextEpisode {@code null} when caught up
	 * @param lastWatchedAt latest watch in the current cycle, or {@code null}
	 */
	public record ShowSnapshot(Counts progress, NextEpisode nextEpisode, Instant lastWatchedAt) {
	}

	/**
	 * An episode of a library show with a known air date.
	 *
	 * @param watched watched in the show's current cycle
	 */
	public record AiringEpisode(long showId, long id, int season, int episode, String name, LocalDate airDate,
			Integer runtimeMinutes, boolean watched) {
	}

	/** An episode with no announced date that has not evidently aired (R5). */
	public record UndatedEpisode(long showId, long id, int season, int episode, String name) {
	}

	public record HistoryEpisode(long id, int season, int episode, String name, Integer runtimeMinutes) {
	}

	/**
	 * One viewing.
	 *
	 * @param inLibrary {@code false} when the show has since been removed (its history is kept, R10)
	 */
	public record HistoryItem(long watchId, Instant watchedAt, int cycle, boolean inLibrary, LibraryShow show,
			HistoryEpisode episode) {
	}

	/** @param nextCursor pass as {@code cursor} for the next page; {@code null} on the last page */
	public record HistoryPage(List<HistoryItem> items, String nextCursor) {
	}

}
