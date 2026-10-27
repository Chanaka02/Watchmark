package app.watchmark.catalog.tmdb;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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
 * Converts TMDB responses into the provider-neutral catalog contract.
 *
 * <p>
 * Defensive by design: blank strings become {@code null}, unparsable dates
 * become {@code null}, items without the ids or numbers needed to address them
 * are dropped, and adult titles are excluded. Nothing is invented.
 */
final class TmdbMapper {

	static final String PROVIDER = "tmdb";

	/** TMDB serves at most 500 pages of any list. */
	static final int MAX_PAGES = 500;

	private final TmdbProperties properties;

	TmdbMapper(TmdbProperties properties) {
		this.properties = properties;
	}

	ResultPage<ShowSummary> page(TmdbResponses.Page page, int requestedPage) {
		if (page == null || page.results() == null) {
			return ResultPage.empty(requestedPage);
		}
		List<ShowSummary> items = page.results()
			.stream()
			.filter(Objects::nonNull)
			.filter(item -> item.id() != null && !Boolean.TRUE.equals(item.adult()))
			.map(this::summary)
			.toList();
		int totalPages = Math.min(orZero(page.totalPages()), MAX_PAGES);
		return new ResultPage<>(items, page.page() == null ? requestedPage : page.page(), totalPages,
				orZero(page.totalResults()));
	}

	ShowSummary summary(TmdbResponses.ShowListItem item) {
		return new ShowSummary(ref(item.id()), name(item.name(), item.originalName()), date(item.firstAirDate()),
				text(item.overview()), image(properties.posterSize(), item.posterPath()), item.voteAverage());
	}

	ShowDetails details(TmdbResponses.ShowDetails show) {
		List<SeasonSummary> seasons = nonNull(show.seasons()).stream()
			.filter(season -> season.seasonNumber() != null && season.seasonNumber() >= 0)
			.map(season -> new SeasonSummary(season.seasonNumber(),
					name(season.name(), "Season " + season.seasonNumber()), season.episodeCount(),
					date(season.airDate()), image(properties.posterSize(), season.posterPath())))
			.sorted(Comparator.comparingInt(SeasonSummary::seasonNumber))
			.toList();
		List<CastMember> cast = show.credits() == null ? List.of()
				: nonNull(show.credits().cast()).stream()
					.filter(member -> text(member.name()) != null)
					.sorted(Comparator.comparing(TmdbResponses.Cast::order,
							Comparator.nullsLast(Comparator.naturalOrder())))
					.limit(ShowDetails.MAX_CAST)
					.map(member -> new CastMember(member.name().strip(), text(member.character()),
							image(properties.profileSize(), member.profilePath())))
					.toList();
		return new ShowDetails(ref(show.id()), name(show.name(), show.originalName()), text(show.originalName()),
				text(show.overview()), text(show.tagline()), status(show.status()), date(show.firstAirDate()),
				date(show.lastAirDate()), image(properties.posterSize(), show.posterPath()),
				image(properties.backdropSize(), show.backdropPath()), names(show.genres()), names(show.networks()),
				firstPositive(show.episodeRunTime()), show.numberOfSeasons(), show.numberOfEpisodes(),
				show.voteAverage(), seasons, episode(show.nextEpisodeToAir()), episode(show.lastEpisodeToAir()),
				cast);
	}

	SeasonDetails season(String externalId, int requestedSeason, TmdbResponses.Season season) {
		int number = season.seasonNumber() == null ? requestedSeason : season.seasonNumber();
		List<EpisodeInfo> episodes = nonNull(season.episodes()).stream()
			.map(this::episode)
			.filter(Objects::nonNull)
			.sorted(Comparator.comparingInt(EpisodeInfo::episodeNumber))
			.toList();
		return new SeasonDetails(new ShowRef(PROVIDER, externalId).toString(), number,
				name(season.name(), number == 0 ? "Specials" : "Season " + number), text(season.overview()),
				date(season.airDate()), image(properties.posterSize(), season.posterPath()), episodes);
	}

	/** {@code null} for episodes that cannot be addressed (no id or numbers). */
	EpisodeInfo episode(TmdbResponses.Episode episode) {
		if (episode == null || episode.id() == null || episode.seasonNumber() == null
				|| episode.episodeNumber() == null || episode.seasonNumber() < 0 || episode.episodeNumber() < 0) {
			return null;
		}
		return new EpisodeInfo(episode.id().toString(), episode.seasonNumber(), episode.episodeNumber(),
				name(episode.name(), "Episode " + episode.episodeNumber()), text(episode.overview()),
				date(episode.airDate()), positive(episode.runtime()), image(properties.stillSize(), episode.stillPath()));
	}

	static ShowStatus status(String status) {
		if (status == null) {
			return ShowStatus.UNKNOWN;
		}
		return switch (status.strip()) {
			case "Returning Series" -> ShowStatus.RETURNING;
			case "In Production" -> ShowStatus.IN_PRODUCTION;
			case "Planned" -> ShowStatus.PLANNED;
			case "Pilot" -> ShowStatus.PILOT;
			case "Ended" -> ShowStatus.ENDED;
			case "Canceled", "Cancelled" -> ShowStatus.CANCELED;
			default -> ShowStatus.UNKNOWN;
		};
	}

	static LocalDate date(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value.strip());
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

	private String image(String size, String path) {
		String clean = text(path);
		if (clean == null || !clean.startsWith("/")) {
			return null;
		}
		return properties.imageBaseUrl().toString().replaceAll("/+$", "") + "/" + size + clean;
	}

	private static String ref(Long id) {
		return new ShowRef(PROVIDER, id.toString()).toString();
	}

	/** Display name with a neutral fallback; the fallback is a label, not data. */
	private static String name(String name, String fallback) {
		String clean = text(name);
		if (clean != null) {
			return clean;
		}
		String cleanFallback = text(fallback);
		return cleanFallback != null ? cleanFallback : "Untitled";
	}

	private static String text(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	private static List<String> names(List<TmdbResponses.Named> values) {
		return nonNull(values).stream().map(named -> text(named.name())).filter(Objects::nonNull).toList();
	}

	private static Integer firstPositive(List<Integer> values) {
		return nonNull(values).stream().filter(value -> value != null && value > 0).findFirst().orElse(null);
	}

	private static Integer positive(Integer value) {
		return value != null && value > 0 ? value : null;
	}

	private static <T> List<T> nonNull(List<T> values) {
		return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
	}

	private static int orZero(Integer value) {
		return value == null || value < 0 ? 0 : value;
	}

}
