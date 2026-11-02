package app.watchmark.tracking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import app.watchmark.catalog.ShowRef;
import app.watchmark.catalog.store.Episode;
import app.watchmark.catalog.store.MetadataSyncService;
import app.watchmark.catalog.store.Show;
import app.watchmark.common.ApiException;
import app.watchmark.library.UserShow;
import app.watchmark.library.UserShowRepository;
import app.watchmark.tracking.ShowTimeline.TimelineEpisode;
import app.watchmark.tracking.dto.TrackingDtos.UndoResult;
import app.watchmark.tracking.dto.TrackingDtos.UnwatchResult;
import app.watchmark.tracking.dto.TrackingDtos.WatchResult;

/**
 * Marking episodes watched and unwatched, and undoing those actions (rules
 * R3–R5, R9 in docs/tracking-rules.md). The user id always comes from the
 * session (R1).
 *
 * <p>
 * Each action runs in one transaction that first locks the user's library entry
 * for the show, so concurrent requests for the same user and show run one after
 * the other. When the entry does not exist yet, two first requests can still
 * collide (duplicate key or deadlock on the gap lock); the action is then retried
 * once and sees the other's result.
 */
@Service
public class TrackingService {

	private final EpisodeWatchRepository watches;

	private final TrackingOperationRepository operations;

	private final UserShowRepository entries;

	private final TimelineLoader timelines;

	private final MetadataSyncService metadata;

	private final EntityManager entityManager;

	private final TransactionTemplate transaction;

	private final TrackingProperties properties;

	private final Clock clock;

	TrackingService(EpisodeWatchRepository watches, TrackingOperationRepository operations,
			UserShowRepository entries, TimelineLoader timelines, MetadataSyncService metadata,
			EntityManager entityManager, TransactionTemplate transaction, TrackingProperties properties, Clock clock) {
		this.watches = watches;
		this.operations = operations;
		this.entries = entries;
		this.timelines = timelines;
		this.metadata = metadata;
		this.entityManager = entityManager;
		this.transaction = transaction;
		this.properties = properties;
		this.clock = clock;
	}

	/** R4: idempotent; R5: only eligible episodes. */
	public WatchResult watch(long userId, long episodeId) {
		return retrying(() -> {
			Episode episode = episode(episodeId);
			Show show = episode.getShow();
			UserShow entry = entries.findForUpdate(userId, show.getId()).orElse(null);
			ShowTimeline timeline = timelines.load(userId, show.getId());
			switch (timeline.eligibility(episodeId)) {
				case NOT_AIRED -> throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "episode-not-aired",
						"Episode not aired yet", "This episode has not aired yet in your time zone.");
				case UNAVAILABLE -> throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "episode-unavailable",
						"Episode unavailable", "This episode is no longer listed for the show.");
				case ELIGIBLE -> {
				}
			}
			return markWatched(userId, show, entry, timeline, List.of(timeline.episode(episodeId).orElseThrow()),
					OperationType.WATCH);
		});
	}

	/** Marks the season's eligible episodes; specials (season 0) included (R7). */
	public WatchResult watchSeason(long userId, ShowRef ref, int seasonNumber) {
		long showId = metadata.ensureStored(ref);
		return retrying(() -> {
			Show show = entityManager.find(Show.class, showId);
			UserShow entry = entries.findForUpdate(userId, showId).orElse(null);
			ShowTimeline timeline = timelines.load(userId, showId);
			List<TimelineEpisode> season = timeline.season(seasonNumber);
			if (season.isEmpty()) {
				throw new ApiException(HttpStatus.NOT_FOUND, "season-not-found", "Season not found",
						"This season has no episodes to track.");
			}
			return markWatched(userId, show, entry, timeline, season, OperationType.SEASON);
		});
	}

	/** Marks every eligible episode up to and including this one (see ShowTimeline#upTo). */
	public WatchResult watchUpTo(long userId, long episodeId) {
		return retrying(() -> {
			Show show = episode(episodeId).getShow();
			UserShow entry = entries.findForUpdate(userId, show.getId()).orElse(null);
			ShowTimeline timeline = timelines.load(userId, show.getId());
			return markWatched(userId, show, entry, timeline, timeline.upTo(episodeId), OperationType.UP_TO);
		});
	}

	/** Soft-deletes the episode's watch in the current cycle; a no-op when not watched. */
	public UnwatchResult unwatch(long userId, long episodeId) {
		return retrying(() -> {
			Show show = episode(episodeId).getShow();
			UserShow entry = entries.findForUpdate(userId, show.getId()).orElse(null);
			int cycle = entry == null ? 1 : entry.getCurrentCycle();
			var watch = watches.findActive(userId, episodeId, cycle);
			if (watch.isEmpty()) {
				return new UnwatchResult(null, show.ref().toString(), 0);
			}
			Instant now = clock.instant();
			TrackingOperation operation = operations.save(
					new TrackingOperation(userId, show.getId(), OperationType.UNWATCH, now));
			watch.get().softDelete(operation.getId(), now);
			reconcile(userId, show, entry);
			return new UnwatchResult(operation.getId(), show.ref().toString(), 1);
		});
	}

	/**
	 * R3: reverts the operation if it is the user's, not undone yet and inside the
	 * undo window. Restoring an unwatch is all-or-nothing.
	 */
	public UndoResult undo(long userId, UUID operationId) {
		return retrying(() -> {
			// Locked so two concurrent undos of the same operation cannot both pass the check.
			TrackingOperation operation = operations.findByIdAndUserId(operationId, userId)
				.orElseThrow(TrackingService::operationNotFound);
			UserShow entry = entries.findForUpdate(userId, operation.getShowId()).orElse(null);
			Instant now = clock.instant();
			if (operation.isUndone()) {
				throw new ApiException(HttpStatus.CONFLICT, "already-undone", "Already undone",
						"This action has already been undone.");
			}
			if (now.isAfter(operation.getCreatedAt().plus(properties.undoWindow()))) {
				throw new ApiException(HttpStatus.CONFLICT, "undo-window-expired", "Undo window expired",
						"Tracking actions can be undone for " + describe(properties.undoWindow()) + ".");
			}

			int reverted;
			if (operation.getType() == OperationType.UNWATCH) {
				List<EpisodeWatch> rows = watches.findDeletedBy(userId, operationId);
				for (EpisodeWatch row : rows) {
					if (watches.findActive(userId, row.getEpisodeId(), row.getCycle()).isPresent()) {
						throw new ApiException(HttpStatus.CONFLICT, "conflicting-watch", "Watched again since",
								"This episode has been marked watched again since, so the unwatch cannot be undone.");
					}
				}
				rows.forEach(EpisodeWatch::restore);
				reverted = rows.size();
			}
			else {
				List<EpisodeWatch> rows = watches.findActiveCreatedBy(userId, operationId);
				rows.forEach(row -> row.softDelete(null, now));
				reverted = rows.size();
			}
			operation.undone(now);

			Show show = entityManager.find(Show.class, operation.getShowId());
			reconcile(userId, show, entry);
			return new UndoResult(operationId, operation.getType(), show.ref().toString(), reverted);
		});
	}

	/**
	 * Creates watch rows for the candidates that are eligible and not yet watched
	 * in the current cycle, under one operation. Adds or restores the library
	 * entry first (R9). Nothing is recorded when nothing would change.
	 */
	private WatchResult markWatched(long userId, Show show, UserShow entry, ShowTimeline timeline,
			List<TimelineEpisode> candidates, OperationType type) {
		int cycle = entry == null ? 1 : entry.getCurrentCycle();
		Set<Long> watched = activeEpisodeIds(userId, show.getId(), cycle);
		List<Long> toCreate = new ArrayList<>();
		int alreadyWatched = 0;
		int notEligible = 0;
		for (TimelineEpisode candidate : candidates) {
			if (watched.contains(candidate.id())) {
				alreadyWatched++;
			}
			else if (!timeline.isEligible(candidate.id())) {
				notEligible++;
			}
			else {
				toCreate.add(candidate.id());
			}
		}
		String showRef = show.ref().toString();
		if (toCreate.isEmpty()) {
			return new WatchResult(null, showRef, 0, alreadyWatched, notEligible);
		}

		Instant now = clock.instant();
		if (entry == null) {
			entry = entries.saveAndFlush(UserShow.startedByTracking(userId, show, now));
		}
		else {
			entry.trackingStarted(now);
		}
		TrackingOperation operation = operations.save(new TrackingOperation(userId, show.getId(), type, now));
		for (Long episodeId : toCreate) {
			watches.save(new EpisodeWatch(userId, episodeId, cycle, operation.getId(), now));
		}
		watches.flush();
		reconcile(userId, show, entry, timeline);
		return new WatchResult(operation.getId(), showRef, toCreate.size(), alreadyWatched, notEligible);
	}

	private void reconcile(long userId, Show show, UserShow entry) {
		if (entry != null) {
			reconcile(userId, show, entry, timelines.load(userId, show.getId()));
		}
	}

	/** R9: status automation after a tracking change. */
	private void reconcile(long userId, Show show, UserShow entry, ShowTimeline timeline) {
		Set<Long> watched = activeEpisodeIds(userId, show.getId(), entry.getCurrentCycle());
		boolean complete = timeline.progress(watched).overall().state() == ShowTimeline.State.COMPLETE;
		entry.reconcileStatus(complete, clock.instant());
	}

	private Set<Long> activeEpisodeIds(long userId, long showId, int cycle) {
		return watches.findActiveInCycle(userId, showId, cycle)
			.stream()
			.map(EpisodeWatch::getEpisodeId)
			.collect(Collectors.toSet());
	}

	private Episode episode(long episodeId) {
		return watches.findEpisode(episodeId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "episode-not-found", "Episode not found",
					"This episode does not exist."));
	}

	private <T> T retrying(Supplier<T> action) {
		try {
			return transaction.execute(tx -> action.get());
		}
		catch (DataIntegrityViolationException | ConcurrencyFailureException ex) {
			// A concurrent first request for the same user and show won (duplicate key or
			// deadlock on the not-yet-existing entry): repeat on top of its result.
			return transaction.execute(tx -> action.get());
		}
	}

	static ApiException operationNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "operation-not-found", "Operation not found",
				"There is no such tracking action to undo.");
	}

	private static String describe(Duration window) {
		long hours = window.toHours();
		return window.equals(Duration.ofHours(hours)) ? hours + (hours == 1 ? " hour" : " hours")
				: window.toMinutes() + " minutes";
	}

}
