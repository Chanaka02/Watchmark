package app.watchmark.calendar;

import java.time.LocalDate;
import java.util.List;

import app.watchmark.library.dto.LibraryDtos.LibraryShow;

/** Calendar API contract (see docs/api.md). */
public final class CalendarDtos {

	private CalendarDtos() {
	}

	/**
	 * @param today the user's today (their time zone)
	 * @param from first day, inclusive
	 * @param to last day, inclusive
	 * @param episodes dated episodes in the range, oldest first
	 * @param unscheduled episodes still to come whose date is not announced, per show
	 */
	public record Calendar(LocalDate today, LocalDate from, LocalDate to, List<CalendarEpisode> episodes,
			List<UnscheduledShow> unscheduled) {
	}

	public record EpisodeInfo(long id, int season, int episode, String name, LocalDate airDate,
			Integer runtimeMinutes) {
	}

	/** @param watched watched in the show's current cycle */
	public record CalendarEpisode(LibraryShow show, EpisodeInfo episode, boolean watched) {
	}

	/** No {@code airDate}: these episodes are never given an estimated one. */
	public record UndatedInfo(long id, int season, int episode, String name) {
	}

	public record UnscheduledShow(LibraryShow show, List<UndatedInfo> episodes) {
	}

}
