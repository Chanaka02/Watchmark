package app.watchmark.calendar;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.watchmark.calendar.CalendarDtos.Calendar;
import app.watchmark.calendar.CalendarDtos.CalendarEpisode;
import app.watchmark.calendar.CalendarDtos.EpisodeInfo;
import app.watchmark.calendar.CalendarDtos.UndatedInfo;
import app.watchmark.calendar.CalendarDtos.UnscheduledShow;
import app.watchmark.common.ApiException;
import app.watchmark.library.LibraryStatus;
import app.watchmark.library.UserShow;
import app.watchmark.library.UserShowRepository;
import app.watchmark.library.dto.LibraryDtos.LibraryShow;
import app.watchmark.tracking.ProgressService;
import app.watchmark.tracking.dto.TrackingDtos.UndatedEpisode;

/**
 * Episodes by air date for the shows a user follows: in the library (not
 * removed, R10) and not dropped. Air dates have no time of day, so they are
 * never shifted between zones; the user's zone only decides "today".
 */
@Service
public class CalendarService {

	/** Longest range, inclusive: a six-week month grid fits with room to spare. */
	static final int MAX_DAYS = 62;

	/** Default range after {@code from}: four weeks including the first day. */
	static final int DEFAULT_EXTRA_DAYS = 27;

	private final UserShowRepository entries;

	private final ProgressService progress;

	CalendarService(UserShowRepository entries, ProgressService progress) {
		this.entries = entries;
		this.progress = progress;
	}

	/**
	 * @param from first day, or {@code null} for the user's today
	 * @param to last day, or {@code null} for {@code from} plus 27 days
	 * @throws ApiException 400 {@code invalid-range}
	 */
	@Transactional(readOnly = true)
	public Calendar get(long userId, LocalDate from, LocalDate to) {
		LocalDate today = progress.today(userId);
		LocalDate first = from == null ? today : from;
		LocalDate last = to == null ? first.plusDays(DEFAULT_EXTRA_DAYS) : to;
		if (last.isBefore(first) || ChronoUnit.DAYS.between(first, last) + 1 > MAX_DAYS) {
			throw invalidRange();
		}

		List<UserShow> following = entries.findLibrary(userId, null)
			.stream()
			.filter(entry -> entry.getStatus() != LibraryStatus.DROPPED)
			.toList();
		Map<Long, LibraryShow> shows = following.stream()
			.collect(Collectors.toMap(entry -> entry.getShow().getId(), entry -> LibraryShow.from(entry.getShow())));

		List<CalendarEpisode> episodes = progress.airing(userId, following, first, last)
			.stream()
			.map(e -> new CalendarEpisode(shows.get(e.showId()),
					new EpisodeInfo(e.id(), e.season(), e.episode(), e.name(), e.airDate(), e.runtimeMinutes()),
					e.watched()))
			.toList();

		Map<Long, List<UndatedEpisode>> undated = progress.undated(userId, following)
			.stream()
			.collect(Collectors.groupingBy(UndatedEpisode::showId, LinkedHashMap::new, Collectors.toList()));
		List<UnscheduledShow> unscheduled = undated.entrySet()
			.stream()
			.map(entry -> new UnscheduledShow(shows.get(entry.getKey()),
					entry.getValue()
						.stream()
						.map(e -> new UndatedInfo(e.id(), e.season(), e.episode(), e.name()))
						.toList()))
			.toList();

		return new Calendar(today, first, last, episodes, unscheduled);
	}

	static ApiException invalidRange() {
		return new ApiException(HttpStatus.BAD_REQUEST, "invalid-range", "Invalid date range",
				"Use dates as YYYY-MM-DD, with 'from' on or before 'to' and at most " + MAX_DAYS + " days in total.");
	}

}
