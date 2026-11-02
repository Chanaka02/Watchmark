package app.watchmark.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import app.watchmark.catalog.ShowStatus;

/**
 * R12: the scheduled purge permanently deletes soft-deleted watch rows after the
 * retention period, then operations nothing references any more, and nothing else.
 */
class TrackingRetentionJobTests extends TrackingTestSupport {

	private static final Duration RETENTION = Duration.ofDays(30);

	@Autowired
	TrackingRetentionJob job;

	@Autowired
	EpisodeWatchRepository watches;

	@Autowired
	TrackingOperationRepository operations;

	@Autowired
	TransactionTemplate transaction;

	User alice;

	String show;

	long e1;

	long e2;

	long e3;

	/** When episode 1 was unwatched. */
	Instant unwatchedAt;

	@BeforeEach
	void setUp() {
		// Other test classes share this database: start the job from a clean slate.
		jdbc.update("DELETE FROM episode_watches");
		jdbc.update("DELETE FROM tracking_operations");
		alice = newUser();
		show = publish(ShowStatus.RETURNING, ep(1, 1, TODAY.minusDays(21)), ep(1, 2, TODAY.minusDays(14)),
				ep(1, 3, TODAY.minusDays(7)));
		addToLibrary(alice, show, "WATCHING");
		e1 = episodeId(show, 1, 1);
		e2 = episodeId(show, 1, 2);
		e3 = episodeId(show, 1, 3);
		watch(alice, e1);
		watch(alice, e2);
		watch(alice, e3);

		clock.advance(Duration.ofHours(1));
		unwatchedAt = clock.instant();
		unwatch(alice, e1);

		clock.advance(Duration.ofHours(1));
		undo(alice, operationId(unwatch(alice, e2)));
	}

	@Test
	void rowsAreKeptUntilTheRetentionPeriodHasPassed() {
		clock.set(unwatchedAt.plus(RETENTION).minus(Duration.ofHours(1)));

		assertThat(job.runOnce()).isEqualTo(new TrackingRetentionJob.Report(0, 0));
		assertThat(allWatchRows(alice)).isEqualTo(3);
	}

	@Test
	void rowsOlderThanTheRetentionPeriodArePurgedWithTheirOperations() {
		clock.set(unwatchedAt.plus(RETENTION).plusSeconds(1));

		TrackingRetentionJob.Report report = job.runOnce();

		assertThat(report.watches()).isEqualTo(1);
		assertThat(report.operations()).as("the watch and unwatch of episode 1").isEqualTo(2);
		assertThat(allWatchRows(alice)).isEqualTo(2);
		assertThat(activeWatches(alice)).as("active watches are never purged").isEqualTo(2);
		assertThat(operations(alice)).as("operations of active rows, and the recent unwatch").isEqualTo(3);
		assertThat(libraryStatus(alice, show)).isEqualTo("WATCHING");
	}

	@Test
	void restoredRowsAreNeverPurgedAndTheirUndoneOperationGoesLater() {
		clock.set(NOW.plus(Duration.ofDays(90)));

		assertThat(job.runOnce()).isEqualTo(new TrackingRetentionJob.Report(1, 3));
		assertThat(activeWatches(alice)).as("episode 2 was restored by undo").isEqualTo(2);
		assertThat(operations(alice)).as("only the watches of the active rows").isEqualTo(2);
		assertThat(job.runOnce()).as("nothing left to purge").isEqualTo(new TrackingRetentionJob.Report(0, 0));
	}

	@Test
	void removedLibraryEntriesAndTheirHistoryAreUntouched() {
		jdbc.update("UPDATE user_shows SET removed_at = ?, rating = 6 WHERE user_id = ?",
				java.sql.Timestamp.from(NOW), alice.id());
		clock.set(NOW.plus(Duration.ofDays(90)));

		job.runOnce();

		assertThat(activeWatches(alice)).isEqualTo(2);
		assertThat(count("SELECT COUNT(*) FROM user_shows WHERE user_id = ? AND removed_at IS NOT NULL AND rating = 6",
				alice.id()))
			.isEqualTo(1);
	}

	@Test
	void nothingIsPurgedWhenThePolicyIsDisabled() {
		TrackingRetentionJob disabled = new TrackingRetentionJob(watches, operations,
				new TrackingProperties(Duration.ofHours(24), RETENTION, false), transaction, clock);
		clock.set(NOW.plus(Duration.ofDays(365)));

		assertThat(disabled.runOnce()).isEqualTo(new TrackingRetentionJob.Report(0, 0));
		assertThat(allWatchRows(alice)).isEqualTo(3);
		assertThat(operations(alice)).isEqualTo(5);
	}

}
