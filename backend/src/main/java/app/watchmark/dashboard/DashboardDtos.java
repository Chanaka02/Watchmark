package app.watchmark.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import app.watchmark.library.dto.LibraryDtos.LibraryEntry;
import app.watchmark.library.dto.LibraryDtos.LibraryShow;
import app.watchmark.tracking.dto.TrackingDtos.Counts;
import app.watchmark.tracking.dto.TrackingDtos.HistoryItem;
import app.watchmark.tracking.dto.TrackingDtos.NextEpisode;

/** Dashboard API contract. Every list is empty, never padded, when the user has no data for it. */
public final class DashboardDtos {

	private DashboardDtos() {
	}

	/**
	 * @param today the user's today (their time zone), which "upcoming" starts from
	 * @param continueWatching shows marked Watching with an aired next episode, most recently watched first
	 * @param upcoming episodes airing from today through 14 days ahead, oldest first
	 * @param recentlyWatched latest viewings of shows still in the library
	 * @param recentlyAdded latest library additions
	 */
	public record Dashboard(LocalDate today, List<ContinueWatching> continueWatching, List<Upcoming> upcoming,
			List<HistoryItem> recentlyWatched, List<LibraryEntry> recentlyAdded) {
	}

	/** @param lastWatchedAt {@code null} when nothing is watched in the current cycle yet */
	public record ContinueWatching(LibraryShow show, int cycle, Counts progress, NextEpisode nextEpisode,
			Instant lastWatchedAt) {
	}

	public record UpcomingEpisodeInfo(long id, int season, int episode, String name, LocalDate airDate) {
	}

	public record Upcoming(LibraryShow show, UpcomingEpisodeInfo episode) {
	}

}
