package app.watchmark.tracking;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * R12: permanently deletes soft-deleted watch rows older than the retention
 * period, then operations no row references any more. Never touches active
 * watches, library entries, ratings, notes, users or metadata.
 *
 * <p>
 * Restored rows have {@code deleted_at = NULL} again, so they are never
 * eligible. The retention period is at least the undo window (enforced by
 * {@link TrackingProperties}), so an undoable operation never loses its rows.
 */
@Component
public class TrackingRetentionJob {

	private static final Logger log = LoggerFactory.getLogger(TrackingRetentionJob.class);

	/**
	 * @param watches soft-deleted watch rows deleted
	 * @param operations operations deleted
	 */
	public record Report(int watches, int operations) {
	}

	private final EpisodeWatchRepository watches;

	private final TrackingOperationRepository operations;

	private final TrackingProperties properties;

	private final TransactionTemplate transaction;

	private final Clock clock;

	TrackingRetentionJob(EpisodeWatchRepository watches, TrackingOperationRepository operations,
			TrackingProperties properties, TransactionTemplate transaction, Clock clock) {
		this.watches = watches;
		this.operations = operations;
		this.properties = properties;
		this.transaction = transaction;
		this.clock = clock;
	}

	@Scheduled(cron = "${watchmark.tracking.purge-cron}", zone = "UTC")
	void scheduledRun() {
		log.info("Tracking retention purge: {}", runOnce());
	}

	public Report runOnce() {
		if (!properties.purgeEnabled()) {
			return new Report(0, 0);
		}
		Instant cutoff = clock.instant().minus(properties.softDeleteRetention());
		return transaction.execute(tx -> {
			int purgedWatches = watches.purgeDeletedBefore(cutoff);
			int purgedOperations = operations.purgeUnreferencedBefore(cutoff);
			return new Report(purgedWatches, purgedOperations);
		});
	}

}
