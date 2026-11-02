package app.watchmark.catalog.store;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import app.watchmark.catalog.MetadataNotFoundException;
import app.watchmark.catalog.MetadataProvider;
import app.watchmark.catalog.MetadataUnavailableException;
import app.watchmark.catalog.ShowRef;
import app.watchmark.catalog.ShowStatus;

/**
 * Keeps stored metadata current and inside TMDB's six-month caching limit.
 *
 * <p>
 * Runs on one instance only (no distributed lock yet: Watchmark is deployed as a
 * single instance). Failures are counted per show and retried on a later run;
 * shows that cannot be refreshed before {@code hard-max-age} are reported at
 * ERROR level so an operator can act before the limit is reached.
 */
@Component
public class MetadataRefreshJob {

	private static final Logger log = LoggerFactory.getLogger(MetadataRefreshJob.class);

	private static final Set<ShowStatus> FINISHED = Set.of(ShowStatus.ENDED, ShowStatus.CANCELED);

	private final ShowRepository shows;

	private final MetadataSyncService sync;

	private final MetadataProvider provider;

	private final RefreshProperties properties;

	private final TransactionTemplate transaction;

	private final Clock clock;

	MetadataRefreshJob(ShowRepository shows, MetadataSyncService sync, MetadataProvider provider,
			RefreshProperties properties, TransactionTemplate transaction, Clock clock) {
		this.shows = shows;
		this.sync = sync;
		this.provider = provider;
		this.properties = properties;
		this.transaction = transaction;
		this.clock = clock;
	}

	/** Grace period before an unreferenced stored show may be deleted. */
	static final Duration UNREFERENCED_GRACE = Duration.ofHours(1);

	/**
	 * @param deleted unreferenced shows removed (they need no refresh, and keeping
	 *     them would only hold TMDB content longer than necessary)
	 */
	public record Report(int deleted, int due, int refreshed, int missing, int failed, long olderThanHardLimit) {
	}

	@Scheduled(cron = "${watchmark.metadata.refresh.cron}", zone = "UTC")
	void scheduledRun() {
		Report report = runOnce();
		log.info("Metadata refresh: {}", report);
	}

	public Report runOnce() {
		Instant now = clock.instant();
		Integer deleted = transaction.execute(tx -> shows.deleteUnreferenced(now.minus(UNREFERENCED_GRACE)));
		List<String> due = shows.findDueForRefresh(provider.id(), FINISHED, now.minus(properties.airingMaxAge()),
				now.minus(properties.finishedMaxAge()), now.minus(properties.hardMaxAge()),
				now.minus(properties.retryAfter()), Pageable.ofSize(properties.batchSize()));

		int refreshed = 0;
		int missing = 0;
		int failed = 0;
		for (String externalId : due) {
			ShowRef ref = new ShowRef(provider.id(), externalId);
			try {
				if (sync.sync(ref).upstreamMissing()) {
					missing++;
				}
				else {
					refreshed++;
				}
			}
			catch (MetadataUnavailableException | MetadataNotFoundException ex) {
				failed++;
			}
			catch (RuntimeException ex) {
				failed++;
				log.error("Unexpected error refreshing {}", ref, ex);
			}
		}

		long tooOld = shows.countByMetadataSyncedAtBefore(now.minus(properties.hardMaxAge()));
		if (tooOld > 0) {
			log.error("{} stored show(s) have metadata older than {} and could not be refreshed; "
					+ "TMDB allows at most six months", tooOld, properties.hardMaxAge());
		}
		return new Report(deleted == null ? 0 : deleted, due.size(), refreshed, missing, failed, tooOld);
	}

}
