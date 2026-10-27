package app.watchmark.catalog.mock;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import app.watchmark.catalog.DiscoverList;
import app.watchmark.catalog.MetadataNotFoundException;
import app.watchmark.catalog.MetadataProvider;
import app.watchmark.catalog.ShowRef;
import app.watchmark.catalog.ShowStatus;
import app.watchmark.catalog.dto.CatalogDtos.CastMember;
import app.watchmark.catalog.dto.CatalogDtos.EpisodeInfo;
import app.watchmark.catalog.dto.CatalogDtos.ResultPage;
import app.watchmark.catalog.dto.CatalogDtos.SeasonDetails;
import app.watchmark.catalog.dto.CatalogDtos.SeasonSummary;
import app.watchmark.catalog.dto.CatalogDtos.ShowDetails;
import app.watchmark.catalog.dto.CatalogDtos.ShowSummary;

/**
 * <b>MOCK DATA.</b> Fictional shows for development and tests when no TMDB
 * token is configured. The UI labels this data as mock.
 *
 * <p>
 * The catalogue deliberately contains the awkward cases real metadata has:
 * specials, episodes with unknown air dates, episodes airing today and in the
 * future, missing runtimes and overviews, an announced season with no episodes,
 * and a planned show with no seasons. Dates are relative to the injected clock
 * (UTC), so "upcoming" stays upcoming and tests stay deterministic. No images
 * are provided: the UI shows placeholders.
 */
public class MockMetadataProvider implements MetadataProvider {

	public static final String PROVIDER = "mock";

	static final int PAGE_SIZE = 6;

	private final Clock clock;

	public MockMetadataProvider(Clock clock) {
		this.clock = clock;
	}

	@Override
	public String id() {
		return PROVIDER;
	}

	@Override
	public boolean isMock() {
		return true;
	}

	@Override
	public ResultPage<ShowSummary> discover(DiscoverList list, int page) {
		LocalDate today = LocalDate.now(clock);
		Map<Integer, MockShow> shows = catalogue(today).stream()
			.collect(Collectors.toMap(MockShow::id, Function.identity()));
		List<MockShow> ordered = switch (list) {
			case TRENDING -> Stream.of(1005, 1001, 1007, 1003, 1002, 1006, 1008, 1004).map(shows::get).toList();
			case POPULAR -> Stream.of(1001, 1006, 1002, 1005, 1007, 1003, 1008, 1004).map(shows::get).toList();
			case TOP_RATED -> shows.values()
				.stream()
				.filter(show -> show.vote() != null)
				.sorted(Comparator.comparing(MockShow::vote).reversed().thenComparing(MockShow::id))
				.toList();
			case ON_THE_AIR -> shows.values()
				.stream()
				.filter(show -> show.airsBetween(today, today.plusDays(7)))
				.sorted(Comparator.comparing(MockShow::id))
				.toList();
		};
		return paginate(ordered, page);
	}

	@Override
	public ResultPage<ShowSummary> search(String query, int page) {
		String needle = query.strip().toLowerCase(Locale.ROOT);
		List<MockShow> matches = catalogue(LocalDate.now(clock)).stream()
			.filter(show -> show.name().toLowerCase(Locale.ROOT).contains(needle))
			.toList();
		return paginate(matches, page);
	}

	@Override
	public ShowDetails show(String externalId) {
		LocalDate today = LocalDate.now(clock);
		MockShow show = find(externalId, today);
		List<EpisodeInfo> regular = show.seasons()
			.stream()
			.filter(season -> season.number() > 0)
			.flatMap(season -> season.episodes().stream().map(episode -> episode.toInfo(show.id(), season.number())))
			.toList();
		EpisodeInfo next = regular.stream()
			.filter(episode -> episode.airDate() != null && episode.airDate().isAfter(today))
			.min(Comparator.comparing(EpisodeInfo::airDate))
			.orElse(null);
		EpisodeInfo last = regular.stream()
			.filter(episode -> episode.airDate() != null && !episode.airDate().isAfter(today))
			.max(Comparator.comparing(EpisodeInfo::airDate))
			.orElse(null);
		List<SeasonSummary> seasons = show.seasons()
			.stream()
			.map(season -> new SeasonSummary(season.number(), season.name(), season.episodes().size(),
					season.airDate(), null))
			.toList();
		LocalDate lastAir = last == null ? null : last.airDate();
		return new ShowDetails(ref(show.id()), show.name(), show.name(), show.overview(), null, show.status(),
				show.firstAirDate(), lastAir, null, null, show.genres(), List.of("Mock Network"), show.runtime(),
				show.seasons().stream().filter(season -> season.number() > 0).toList().size(), regular.size(),
				show.vote(), seasons, next, last, show.cast());
	}

	@Override
	public SeasonDetails season(String externalId, int seasonNumber) {
		MockShow show = find(externalId, LocalDate.now(clock));
		MockSeason season = show.seasons()
			.stream()
			.filter(candidate -> candidate.number() == seasonNumber)
			.findFirst()
			.orElseThrow(MetadataNotFoundException::season);
		List<EpisodeInfo> episodes = season.episodes()
			.stream()
			.map(episode -> episode.toInfo(show.id(), season.number()))
			.toList();
		return new SeasonDetails(ref(show.id()), season.number(), season.name(), null, season.airDate(), null,
				episodes);
	}

	private MockShow find(String externalId, LocalDate today) {
		return catalogue(today).stream()
			.filter(show -> Integer.toString(show.id()).equals(externalId))
			.findFirst()
			.orElseThrow(MetadataNotFoundException::show);
	}

	private static ResultPage<ShowSummary> paginate(List<MockShow> shows, int page) {
		int totalPages = (shows.size() + PAGE_SIZE - 1) / PAGE_SIZE;
		List<ShowSummary> items = shows.stream()
			.skip((long) (page - 1) * PAGE_SIZE)
			.limit(PAGE_SIZE)
			.map(show -> new ShowSummary(ref(show.id()), show.name(), show.firstAirDate(), show.overview(), null,
					show.vote()))
			.toList();
		return new ResultPage<>(items, page, totalPages, shows.size());
	}

	private static String ref(int id) {
		return new ShowRef(PROVIDER, Integer.toString(id)).toString();
	}

	// --- Fictional catalogue ------------------------------------------------------

	static List<MockShow> catalogue(LocalDate today) {
		return List.of(
				new MockShow(1001, "Northern Lights Station",
						"Fictional mock show: researchers at a remote Arctic station uncover a signal under the ice.",
						ShowStatus.RETURNING, List.of("Drama", "Mystery"), 50, 8.4,
						List.of(season(0, "Specials", null, episode(1, today.minusDays(400), 25),
								episode(2, null, null)),
								season(1, "Season 1", today.minusDays(700), weekly(8, today.minusDays(700), 50)),
								season(2, "Season 2", today.minusDays(21),
										episode(1, today.minusDays(21), 50), episode(2, today.minusDays(14), 50),
										episode(3, today.minusDays(7), 50), episode(4, today, 50),
										episode(5, today.plusDays(7), 50), episode(6, today.plusDays(14), null)),
								season(3, "Season 3", null)),
						cast("Avery Example", "Dr. Ilse Varga", "Rowan Sample", "Teo Lind")),
				new MockShow(1002, "The Quiet Archive",
						"Fictional mock show: an archivist catalogues lost radio plays. Older listings have gaps.",
						ShowStatus.ENDED, List.of("Drama"), 30, 7.9,
						List.of(season(1, "Season 1", today.minusDays(3000),
								episode(1, today.minusDays(3000), null), episode(2, today.minusDays(2993), null),
								episode(3, null, null), episode(4, null, null),
								episode(5, today.minusDays(2972), null), episode(6, today.minusDays(2965), null)),
								season(2, "Season 2", today.minusDays(2600), weekly(6, today.minusDays(2600), null))),
						cast("Morgan Placeholder", "Edith Crane")),
				new MockShow(1003, "Harbor Lights", null, ShowStatus.CANCELED, List.of("Crime"), 45, 6.1,
						List.of(season(1, "Season 1", today.minusDays(500), weekly(6, today.minusDays(500), 45))),
						List.of()),
				new MockShow(1004, "Paper Satellites",
						"Fictional mock show: announced, with no seasons or dates published yet.", ShowStatus.PLANNED,
						List.of("Science Fiction"), null, null, List.of(), List.of()),
				new MockShow(1005, "Midnight Recipe Club",
						"Fictional mock show: a late-night cooking competition airing weekly.", ShowStatus.RETURNING,
						List.of("Reality"), 42, 7.2,
						List.of(season(1, "Season 1", today.minusDays(49), weekly(12, today.minusDays(49), 42))),
						cast("Sam Fixture", "Host")),
				new MockShow(1006, "Lighthouse Keepers' Almanac",
						"Fictional mock show: an anthology of stories from coastal lighthouses.", ShowStatus.ENDED,
						List.of("Documentary", "History"), 55, 8.8,
						List.of(season(1, "Season 1", today.minusDays(1500), weekly(8, today.minusDays(1500), 55)),
								season(2, "Season 2", today.minusDays(1100), weekly(8, today.minusDays(1100), 55)),
								season(3, "Season 3", today.minusDays(700), weekly(8, today.minusDays(700), 55))),
						cast("Jordan Stub", "Narrator")),
				new MockShow(1007, "Static & Starlight",
						"Fictional mock show: a pirate radio crew; season 2 premieres next month.", ShowStatus.RETURNING,
						List.of("Comedy", "Drama"), 28, 7.5,
						List.of(season(1, "Season 1", today.minusDays(400), weekly(10, today.minusDays(400), 28)),
								season(2, "Season 2", today.plusDays(30), weekly(10, today.plusDays(30), 28))),
						cast("Casey Demo", "DJ Nova", "Riley Mockwell", "Pip")),
				new MockShow(1008, "Ninety Minutes North",
						"Fictional mock show: in production; episode dates have not been announced.",
						ShowStatus.IN_PRODUCTION, List.of("Thriller"), null, null,
						List.of(season(1, "Season 1", null, undated(8))), List.of()));
	}

	private static MockSeason season(int number, String name, LocalDate airDate, MockEpisode... episodes) {
		return new MockSeason(number, name, airDate, List.of(episodes));
	}

	private static MockSeason season(int number, String name, LocalDate airDate, List<MockEpisode> episodes) {
		return new MockSeason(number, name, airDate, episodes);
	}

	private static MockEpisode episode(int number, LocalDate airDate, Integer runtime) {
		return new MockEpisode(number, airDate, runtime);
	}

	private static List<MockEpisode> weekly(int count, LocalDate first, Integer runtime) {
		List<MockEpisode> episodes = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			episodes.add(new MockEpisode(i + 1, first.plusWeeks(i), runtime));
		}
		return episodes;
	}

	private static List<MockEpisode> undated(int count) {
		List<MockEpisode> episodes = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			episodes.add(new MockEpisode(i + 1, null, null));
		}
		return episodes;
	}

	private static List<CastMember> cast(String... namesAndRoles) {
		List<CastMember> cast = new ArrayList<>();
		for (int i = 0; i + 1 < namesAndRoles.length; i += 2) {
			cast.add(new CastMember(namesAndRoles[i], namesAndRoles[i + 1], null));
		}
		return cast;
	}

	record MockShow(int id, String name, String overview, ShowStatus status, List<String> genres, Integer runtime,
			Double vote, List<MockSeason> seasons, List<CastMember> cast) {

		LocalDate firstAirDate() {
			return seasons.stream()
				.filter(season -> season.number() > 0)
				.flatMap(season -> season.episodes().stream())
				.map(MockEpisode::airDate)
				.filter(Objects::nonNull)
				.min(Comparator.naturalOrder())
				.orElse(null);
		}

		boolean airsBetween(LocalDate from, LocalDate to) {
			return seasons.stream()
				.flatMap(season -> season.episodes().stream())
				.map(MockEpisode::airDate)
				.anyMatch(date -> date != null && !date.isBefore(from) && !date.isAfter(to));
		}

	}

	record MockSeason(int number, String name, LocalDate airDate, List<MockEpisode> episodes) {
	}

	record MockEpisode(int number, LocalDate airDate, Integer runtime) {

		EpisodeInfo toInfo(int showId, int seasonNumber) {
			String externalId = Integer.toString(showId * 10_000 + seasonNumber * 100 + number);
			return new EpisodeInfo(externalId, seasonNumber, number, "Episode " + number, null, airDate, runtime,
					null);
		}

	}

}
