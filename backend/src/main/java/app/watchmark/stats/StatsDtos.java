package app.watchmark.stats;

import java.time.LocalDate;
import java.util.List;

import app.watchmark.library.dto.LibraryDtos.LibraryShow;

/** Statistics API contract (see docs/api.md). Nothing in it is estimated. */
public final class StatsDtos {

	private StatsDtos() {
	}

	/**
	 * @param from first local date covered, {@code null} for {@code all}
	 * @param to last local date covered: the user's today
	 * @param minutesWatched sum of each viewing's episode runtime
	 * @param watchesWithUnknownRuntime viewings left out of {@code minutesWatched}
	 */
	public record Stats(String range, LocalDate from, LocalDate to, long uniqueEpisodes, long totalWatches,
			long minutesWatched, long watchesWithUnknownRuntime, long showsWatched, LibraryCounts library,
			List<ShowStat> topShows, List<GenreStat> topGenres, Activity activity) {
	}

	/** Shows currently in the library (not removed) with these statuses; not range-dependent. */
	public record LibraryCounts(long watching, long completed) {
	}

	public record ShowStat(LibraryShow show, long watches, long minutes) {
	}

	/** A viewing counts once for each genre of its show. */
	public record GenreStat(String name, long watches, long minutes) {
	}

	public enum Unit {

		DAY, MONTH

	}

	/** @param points every period in order, zeros included */
	public record Activity(Unit unit, List<Point> points) {
	}

	/** @param period {@code YYYY-MM-DD} for days, {@code YYYY-MM} for months */
	public record Point(String period, long watches, long minutes) {
	}

}
