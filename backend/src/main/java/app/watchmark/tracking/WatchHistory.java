package app.watchmark.tracking;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Watch history as a whole: totals (R2) and permanent deletion (R11, account deletion). */
@Component
public class WatchHistory {

	/**
	 * @param uniqueEpisodes distinct episodes with an active watch
	 * @param totalWatches active watches, each rewatch counted again
	 */
	public record Totals(long uniqueEpisodes, long totalWatches) {
	}

	private final EpisodeWatchRepository watches;

	private final TrackingOperationRepository operations;

	WatchHistory(EpisodeWatchRepository watches, TrackingOperationRepository operations) {
		this.watches = watches;
		this.operations = operations;
	}

	/** Every show, including removed ones (R10); soft-deleted rows excluded. */
	@Transactional(readOnly = true)
	public Totals totals(long userId) {
		return new Totals(watches.countUniqueEpisodes(userId), watches.countWatches(userId));
	}

	/** R11: the user's watch rows (active and soft-deleted) and operations for one show. */
	@Transactional
	public void deleteForShow(long userId, long showId) {
		// Rows first: they reference the operations (RESTRICT).
		watches.deleteForShow(userId, showId);
		operations.deleteForShow(userId, showId);
	}

	/** Account deletion: explicit, so the order does not depend on cascade ordering. */
	@Transactional
	public void deleteForUser(long userId) {
		watches.deleteForUser(userId);
		operations.deleteForUser(userId);
	}

}
