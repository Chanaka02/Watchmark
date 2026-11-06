package app.watchmark.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.watchmark.dashboard.DashboardDtos.ContinueWatching;
import app.watchmark.dashboard.DashboardDtos.Dashboard;
import app.watchmark.dashboard.DashboardDtos.Upcoming;
import app.watchmark.dashboard.DashboardDtos.UpcomingEpisodeInfo;
import app.watchmark.library.LibraryStatus;
import app.watchmark.library.UserShow;
import app.watchmark.library.UserShowRepository;
import app.watchmark.library.dto.LibraryDtos.LibraryEntry;
import app.watchmark.library.dto.LibraryDtos.LibraryShow;
import app.watchmark.tracking.HistoryService;
import app.watchmark.tracking.ProgressService;
import app.watchmark.tracking.dto.TrackingDtos.ShowSnapshot;

/**
 * The signed-in home page. Built only from the user's own library and history
 * (R1); removed shows never appear (R10).
 */
@Service
public class DashboardService {

	static final int CONTINUE_LIMIT = 12;

	static final int UPCOMING_DAYS = 14;

	static final int UPCOMING_LIMIT = 20;

	static final int RECENTLY_WATCHED_LIMIT = 10;

	static final int RECENTLY_ADDED_LIMIT = 6;

	private final UserShowRepository entries;

	private final ProgressService progress;

	private final HistoryService history;

	DashboardService(UserShowRepository entries, ProgressService progress, HistoryService history) {
		this.entries = entries;
		this.progress = progress;
		this.history = history;
	}

	@Transactional(readOnly = true)
	public Dashboard get(long userId) {
		List<UserShow> library = entries.findLibrary(userId, null);
		return new Dashboard(progress.today(userId), continueWatching(userId, library), upcoming(userId, library),
				history.recentInLibrary(userId, RECENTLY_WATCHED_LIMIT), recentlyAdded(library));
	}

	private List<ContinueWatching> continueWatching(long userId, List<UserShow> library) {
		List<UserShow> watching = library.stream().filter(e -> e.getStatus() == LibraryStatus.WATCHING).toList();
		Map<Long, ShowSnapshot> snapshots = progress.snapshots(userId, watching);
		record Candidate(UserShow entry, ShowSnapshot snapshot) {
		}
		// Most recently watched first; shows not started in this cycle last, latest change first.
		Comparator<Candidate> order = Comparator
			.comparing((Candidate c) -> c.snapshot().lastWatchedAt(),
					Comparator.nullsLast(Comparator.<Instant>reverseOrder()))
			.thenComparing(c -> c.entry().getUpdatedAt(), Comparator.reverseOrder());
		return watching.stream()
			.map(entry -> new Candidate(entry, snapshots.get(entry.getShow().getId())))
			.filter(c -> c.snapshot().nextEpisode() != null)
			.sorted(order)
			.limit(CONTINUE_LIMIT)
			.map(c -> new ContinueWatching(LibraryShow.from(c.entry().getShow()), c.entry().getCurrentCycle(),
					c.snapshot().progress(), c.snapshot().nextEpisode(), c.snapshot().lastWatchedAt()))
			.toList();
	}

	private List<Upcoming> upcoming(long userId, List<UserShow> library) {
		List<UserShow> following = library.stream().filter(e -> e.getStatus() != LibraryStatus.DROPPED).toList();
		Map<Long, UserShow> byShow = following.stream()
			.collect(Collectors.toMap(e -> e.getShow().getId(), Function.identity()));
		LocalDate today = progress.today(userId);
		return progress.airing(userId, following, today, today.plusDays(UPCOMING_DAYS))
			.stream()
			.filter(e -> !e.watched())
			.limit(UPCOMING_LIMIT)
			.map(e -> new Upcoming(LibraryShow.from(byShow.get(e.showId()).getShow()),
					new UpcomingEpisodeInfo(e.id(), e.season(), e.episode(), e.name(), e.airDate())))
			.toList();
	}

	private static List<LibraryEntry> recentlyAdded(List<UserShow> library) {
		return library.stream()
			.sorted(Comparator.comparing(UserShow::getAddedAt).reversed().thenComparing(UserShow::getId,
					Comparator.reverseOrder()))
			.limit(RECENTLY_ADDED_LIMIT)
			.map(LibraryEntry::from)
			.toList();
	}

}
