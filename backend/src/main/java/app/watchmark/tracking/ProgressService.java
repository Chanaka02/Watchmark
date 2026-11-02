package app.watchmark.tracking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.watchmark.catalog.ShowRef;
import app.watchmark.catalog.store.Episode;
import app.watchmark.common.ApiException;
import app.watchmark.library.UserShow;
import app.watchmark.library.UserShowRepository;
import app.watchmark.library.dto.LibraryDtos.LibraryEntry;
import app.watchmark.tracking.ShowTimeline.Progress;
import app.watchmark.tracking.dto.TrackingDtos.Counts;
import app.watchmark.tracking.dto.TrackingDtos.EpisodeProgress;
import app.watchmark.tracking.dto.TrackingDtos.History;
import app.watchmark.tracking.dto.TrackingDtos.NextEpisode;
import app.watchmark.tracking.dto.TrackingDtos.SeasonProgress;
import app.watchmark.tracking.dto.TrackingDtos.SeasonSummary;
import app.watchmark.tracking.dto.TrackingDtos.ShowProgress;
import app.watchmark.tracking.dto.TrackingDtos.ShowSnapshot;
import app.watchmark.tracking.dto.TrackingDtos.UpcomingEpisode;

/**
 * Progress of a show in the user's library (rules R6, R7) and rewatching. Only
 * for shows in the library: a removed show has no progress page (R10), but its
 * history is kept and comes back when it is re-added.
 */
@Service
public class ProgressService {

	private final UserShowRepository entries;

	private final EpisodeWatchRepository watches;

	private final TimelineLoader timelines;

	private final Clock clock;

	ProgressService(UserShowRepository entries, EpisodeWatchRepository watches, TimelineLoader timelines,
			Clock clock) {
		this.entries = entries;
		this.watches = watches;
		this.timelines = timelines;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ShowProgress show(long userId, ShowRef ref) {
		UserShow entry = activeEntry(userId, ref);
		long showId = entry.getShow().getId();
		ShowTimeline timeline = timelines.load(userId, showId);
		Map<Long, EpisodeWatch> watched = watchedInCycle(userId, entry);
		Progress progress = timeline.progress(watched.keySet());

		List<SeasonSummary> seasons = progress.seasons()
			.entrySet()
			.stream()
			.map(season -> {
				var counts = season.getValue();
				return new SeasonSummary(season.getKey(), season.getKey() == 0, counts.watched(), counts.eligible(),
						counts.total(), counts.percent(), counts.state());
			})
			.toList();
		NextEpisode next = progress.nextEpisodeId() == null ? null
				: watches.findEpisode(progress.nextEpisodeId())
					.map(e -> new NextEpisode(e.getId(), e.getSeasonNumber(), e.getEpisodeNumber(), e.getName(),
							e.getAirDate()))
					.orElse(null);
		History history = new History(watches.countUniqueEpisodes(userId, showId),
				watches.countWatches(userId, showId));
		return new ShowProgress(ref.toString(), entry.getCurrentCycle(), entry.getStatus(), counts(progress.overall()),
				history, seasons, next, !watched.isEmpty());
	}

	/**
	 * Episodes of one season with their state. Episodes no longer listed upstream
	 * are included only while watched in the current cycle (R8).
	 */
	@Transactional(readOnly = true)
	public SeasonProgress season(long userId, ShowRef ref, int seasonNumber) {
		UserShow entry = activeEntry(userId, ref);
		long showId = entry.getShow().getId();
		ShowTimeline timeline = timelines.load(userId, showId);
		Map<Long, EpisodeWatch> watched = watchedInCycle(userId, entry);

		List<EpisodeProgress> episodes = watches.findEpisodesOfShow(showId)
			.stream()
			.filter(e -> e.getSeasonNumber() == seasonNumber && (e.isActive() || watched.containsKey(e.getId())))
			.sorted(Comparator.comparingInt(Episode::getEpisodeNumber).thenComparing(Episode::getId))
			.map(e -> {
				EpisodeWatch watch = watched.get(e.getId());
				Instant watchedAt = watch == null ? null : watch.getWatchedAt();
				return new EpisodeProgress(e.getId(), e.getExternalId(), e.getEpisodeNumber(), e.getName(),
						e.getAirDate(), timeline.isEligible(e.getId()), watch != null, watchedAt, !e.isActive());
			})
			.toList();
		if (episodes.isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "season-not-found", "Season not found",
					"This season has no episodes to track.");
		}
		var counts = timeline.progress(watched.keySet()).season(seasonNumber);
		return new SeasonProgress(ref.toString(), entry.getCurrentCycle(), seasonNumber, seasonNumber == 0,
				counts.watched(), counts.eligible(), counts.total(), counts.percent(), counts.state(), episodes);
	}

	/**
	 * Starts the next cycle. Requires a watch in the current one, so empty cycles
	 * cannot pile up. Not an operation: it cannot be undone.
	 */
	@Transactional
	public LibraryEntry rewatch(long userId, ShowRef ref) {
		UserShow entry = entries.findForUpdate(userId, activeEntry(userId, ref).getShow().getId())
			.orElseThrow(ProgressService::notInLibrary);
		if (watchedInCycle(userId, entry).isEmpty()) {
			throw new ApiException(HttpStatus.CONFLICT, "nothing-to-rewatch", "Nothing to rewatch",
					"Mark at least one episode watched before starting a rewatch.");
		}
		entry.startRewatch(clock.instant());
		return LibraryEntry.from(entry);
	}

	/** Today in the user's time zone (R5). */
	public LocalDate today(long userId) {
		return timelines.today(userId);
	}

	/**
	 * Progress, next episode and last watch for several library entries, keyed by
	 * show id. Loads every show's episodes and watches in two queries.
	 */
	@Transactional(readOnly = true)
	public Map<Long, ShowSnapshot> snapshots(long userId, Collection<UserShow> library) {
		if (library.isEmpty()) {
			return Map.of();
		}
		Map<Long, UserShow> byShow = new HashMap<>();
		library.forEach(entry -> byShow.put(entry.getShow().getId(), entry));
		Map<Long, List<Episode>> episodes = watches.findEpisodesOfShows(byShow.keySet())
			.stream()
			.collect(Collectors.groupingBy(e -> e.getShow().getId()));
		Map<Long, Set<Long>> watched = new HashMap<>();
		Map<Long, Instant> lastWatched = new HashMap<>();
		for (EpisodeWatch row : watches.findActiveForShows(userId, byShow.keySet())) {
			long showId = row.getEpisode().getShow().getId();
			if (row.getCycle() != byShow.get(showId).getCurrentCycle()) {
				continue;
			}
			watched.computeIfAbsent(showId, id -> new HashSet<>()).add(row.getEpisodeId());
			lastWatched.merge(showId, row.getWatchedAt(), (a, b) -> a.isAfter(b) ? a : b);
		}

		LocalDate today = timelines.today(userId);
		Map<Long, ShowSnapshot> snapshots = new HashMap<>();
		byShow.forEach((showId, entry) -> {
			List<Episode> showEpisodes = episodes.getOrDefault(showId, List.of());
			Progress progress = TimelineLoader.of(showEpisodes, today)
				.progress(watched.getOrDefault(showId, Set.of()));
			NextEpisode next = progress.nextEpisodeId() == null ? null
					: showEpisodes.stream()
						.filter(e -> e.getId().equals(progress.nextEpisodeId()))
						.findFirst()
						.map(e -> new NextEpisode(e.getId(), e.getSeasonNumber(), e.getEpisodeNumber(), e.getName(),
								e.getAirDate()))
						.orElse(null);
			snapshots.put(showId, new ShowSnapshot(counts(progress.overall()), next, lastWatched.get(showId)));
		});
		return snapshots;
	}

	/**
	 * Episodes of these library shows airing from today through {@code days}
	 * days ahead in the user's time zone, oldest first, leaving out episodes
	 * already watched in the current cycle. Undated episodes are never included.
	 */
	@Transactional(readOnly = true)
	public List<UpcomingEpisode> upcoming(long userId, Collection<UserShow> library, int days, int limit) {
		if (library.isEmpty()) {
			return List.of();
		}
		Map<Long, UserShow> byShow = new HashMap<>();
		library.forEach(entry -> byShow.put(entry.getShow().getId(), entry));
		LocalDate today = timelines.today(userId);
		List<Episode> airing = watches.findAiringBetween(byShow.keySet(), today, today.plusDays(days));
		if (airing.isEmpty()) {
			return List.of();
		}
		Set<Long> watchedInCycle = new HashSet<>();
		for (EpisodeWatch row : watches.findActiveForEpisodes(userId, airing.stream().map(Episode::getId).toList())) {
			if (row.getCycle() == byShow.get(row.getEpisode().getShow().getId()).getCurrentCycle()) {
				watchedInCycle.add(row.getEpisodeId());
			}
		}
		return airing.stream()
			.filter(e -> !watchedInCycle.contains(e.getId()))
			.limit(limit)
			.map(e -> new UpcomingEpisode(e.getShow().getId(), e.getId(), e.getSeasonNumber(), e.getEpisodeNumber(),
					e.getName(), e.getAirDate()))
			.toList();
	}

	private Map<Long, EpisodeWatch> watchedInCycle(long userId, UserShow entry) {
		return watches.findActiveInCycle(userId, entry.getShow().getId(), entry.getCurrentCycle())
			.stream()
			.collect(Collectors.toMap(EpisodeWatch::getEpisodeId, Function.identity()));
	}

	private UserShow activeEntry(long userId, ShowRef ref) {
		return entries.findEntry(userId, ref.provider(), ref.externalId())
			.filter(entry -> !entry.isRemoved())
			.orElseThrow(ProgressService::notInLibrary);
	}

	private static Counts counts(ShowTimeline.Counts counts) {
		return new Counts(counts.watched(), counts.eligible(), counts.total(), counts.percent(), counts.state());
	}

	private static ApiException notInLibrary() {
		return new ApiException(HttpStatus.NOT_FOUND, "not-in-library", "Not in library",
				"This show is not in your library.");
	}

}
