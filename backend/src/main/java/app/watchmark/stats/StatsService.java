package app.watchmark.stats;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.watchmark.catalog.store.Show;
import app.watchmark.common.ApiException;
import app.watchmark.library.LibraryStatus;
import app.watchmark.library.UserShowRepository;
import app.watchmark.library.dto.LibraryDtos.LibraryShow;
import app.watchmark.stats.StatsDtos.Activity;
import app.watchmark.stats.StatsDtos.GenreStat;
import app.watchmark.stats.StatsDtos.LibraryCounts;
import app.watchmark.stats.StatsDtos.Point;
import app.watchmark.stats.StatsDtos.ShowStat;
import app.watchmark.stats.StatsDtos.Stats;
import app.watchmark.stats.StatsDtos.Unit;
import app.watchmark.stats.StatsRepository.Viewing;
import app.watchmark.user.User;
import app.watchmark.user.UserRepository;

/**
 * Viewing statistics (rule R2): active watch rows of every cycle, removed shows
 * included (R10). Ranges and activity buckets are calendar days in the user's
 * time zone. Runtimes are the episodes' own; unknown ones are counted
 * separately, never guessed.
 */
@Service
public class StatsService {

	public enum Range {

		/** The last 7 days including today. */
		WEEK(7),
		/** The last 30 days including today. */
		MONTH(30),
		/** Everything. */
		ALL(0);

		private final int days;

		Range(int days) {
			this.days = days;
		}

		static Range parse(String value) {
			for (Range range : values()) {
				if (range.name().equalsIgnoreCase(value)) {
					return range;
				}
			}
			throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-range", "Invalid range",
					"Use range=week, month or all.");
		}

	}

	static final int TOP_SHOWS = 5;

	static final int TOP_GENRES = 10;

	/** Viewings and minutes, accumulated per show, genre or period. */
	private static final class Tally {

		long watches;

		long minutes;

		void add(long watchCount, long minuteCount) {
			watches += watchCount;
			minutes += minuteCount;
		}

	}

	private final StatsRepository stats;

	private final UserShowRepository entries;

	private final UserRepository users;

	private final Clock clock;

	StatsService(StatsRepository stats, UserShowRepository entries, UserRepository users, Clock clock) {
		this.stats = stats;
		this.entries = entries;
		this.users = users;
		this.clock = clock;
	}

	/** @param rangeName {@code week}, {@code month} or {@code all}; {@code null} means {@code month} */
	@Transactional(readOnly = true)
	public Stats get(long userId, String rangeName) {
		Range range = rangeName == null ? Range.MONTH : Range.parse(rangeName);
		ZoneId zone = users.findById(userId).map(User::getTimeZone).map(ZoneId::of).orElse(ZoneOffset.UTC);
		LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
		LocalDate from = range == Range.ALL ? null : today.minusDays(range.days - 1L);
		Instant start = from == null ? Instant.EPOCH : from.atStartOfDay(zone).toInstant();
		Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
		List<Viewing> viewings = stats.findViewings(userId, start, end);

		long minutes = 0;
		long unknownRuntime = 0;
		Set<Long> episodes = new HashSet<>();
		Map<Long, Tally> perShow = new HashMap<>();
		for (Viewing viewing : viewings) {
			episodes.add(viewing.getEpisodeId());
			minutes += runtime(viewing);
			if (viewing.getRuntime() == null) {
				unknownRuntime++;
			}
			perShow.computeIfAbsent(viewing.getShowId(), id -> new Tally()).add(1, runtime(viewing));
		}

		return new Stats(range.name().toLowerCase(Locale.ROOT), from, today, episodes.size(), viewings.size(),
				minutes, unknownRuntime, perShow.size(), libraryCounts(userId), topShows(perShow),
				topGenres(perShow), activity(range, viewings, zone, from, today));
	}

	private LibraryCounts libraryCounts(long userId) {
		Map<LibraryStatus, Long> counts = new HashMap<>();
		entries.countByStatus(userId).forEach(count -> counts.put(count.getStatus(), count.getCount()));
		return new LibraryCounts(counts.getOrDefault(LibraryStatus.WATCHING, 0L),
				counts.getOrDefault(LibraryStatus.COMPLETED, 0L));
	}

	private List<ShowStat> topShows(Map<Long, Tally> perShow) {
		if (perShow.isEmpty()) {
			return List.of();
		}
		Map<Long, Show> shows = stats.findShows(perShow.keySet())
			.stream()
			.collect(Collectors.toMap(Show::getId, Function.identity()));
		return perShow.entrySet()
			.stream()
			.map(e -> new ShowStat(LibraryShow.from(shows.get(e.getKey())), e.getValue().watches,
					e.getValue().minutes))
			.sorted(Comparator.comparingLong(ShowStat::watches)
				.thenComparingLong(ShowStat::minutes)
				.reversed()
				.thenComparing(stat -> stat.show().name()))
			.limit(TOP_SHOWS)
			.toList();
	}

	/** Each viewing counts once for every genre of its show; shows without genres are not counted. */
	private List<GenreStat> topGenres(Map<Long, Tally> perShow) {
		if (perShow.isEmpty()) {
			return List.of();
		}
		Map<String, Tally> perGenre = new HashMap<>();
		for (var genre : stats.findGenres(perShow.keySet())) {
			Tally show = perShow.get(genre.getShowId());
			perGenre.computeIfAbsent(genre.getName(), name -> new Tally()).add(show.watches, show.minutes);
		}
		return perGenre.entrySet()
			.stream()
			.map(e -> new GenreStat(e.getKey(), e.getValue().watches, e.getValue().minutes))
			.sorted(Comparator.comparingLong(GenreStat::watches)
				.thenComparingLong(GenreStat::minutes)
				.reversed()
				.thenComparing(GenreStat::name))
			.limit(TOP_GENRES)
			.toList();
	}

	/** Every day of a bounded range, or every month since the first viewing; zeros included. */
	private static Activity activity(Range range, List<Viewing> viewings, ZoneId zone, LocalDate from,
			LocalDate today) {
		Unit unit = range == Range.ALL ? Unit.MONTH : Unit.DAY;
		Map<String, Tally> buckets = new TreeMap<>();
		for (Viewing viewing : viewings) {
			LocalDate day = LocalDate.ofInstant(viewing.getWatchedAt(), zone);
			String period = unit == Unit.DAY ? day.toString() : YearMonth.from(day).toString();
			buckets.computeIfAbsent(period, p -> new Tally()).add(1, runtime(viewing));
		}

		List<String> periods = new ArrayList<>();
		if (unit == Unit.DAY) {
			for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
				periods.add(day.toString());
			}
		}
		else if (!buckets.isEmpty()) {
			YearMonth last = YearMonth.from(today);
			// TreeMap: the first key is the earliest month ("YYYY-MM" sorts chronologically).
			for (YearMonth month = YearMonth.parse(buckets.keySet().iterator().next()); !month.isAfter(last);
					month = month.plusMonths(1)) {
				periods.add(month.toString());
			}
		}
		return new Activity(unit, periods.stream().map(period -> {
			Tally bucket = buckets.getOrDefault(period, new Tally());
			return new Point(period, bucket.watches, bucket.minutes);
		}).toList());
	}

	private static int runtime(Viewing viewing) {
		return viewing.getRuntime() == null ? 0 : viewing.getRuntime();
	}

}
