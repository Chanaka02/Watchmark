package app.watchmark.catalog.dto;

import java.time.LocalDate;
import java.util.List;

import app.watchmark.catalog.ShowStatus;

/**
 * Provider-neutral catalog API contract. Every provider maps into these types.
 *
 * <p>
 * Nullability is part of the contract: providers often omit data, so any field
 * documented as nullable may be {@code null} and clients must handle it. Image
 * fields are absolute URLs or {@code null}; dates are calendar dates with no time
 * zone ({@code air date as published}).
 */
public final class CatalogDtos {

	private CatalogDtos() {
	}

	/**
	 * @param provider active provider id, e.g. {@code tmdb}
	 * @param mock true when data is fictional development data
	 */
	public record CatalogSource(String provider, boolean mock) {
	}

	public record ResultPage<T>(List<T> items, int page, int totalPages, int totalResults) {

		public static <T> ResultPage<T> empty(int page) {
			return new ResultPage<>(List.of(), page, 0, 0);
		}

	}

	/**
	 * A show in a list or search result.
	 * @param firstAirDate nullable
	 * @param overview nullable
	 * @param posterUrl nullable
	 * @param voteAverage nullable, 0–10
	 */
	public record ShowSummary(String ref, String name, LocalDate firstAirDate, String overview, String posterUrl,
			Double voteAverage) {
	}

	/**
	 * @param episodeRunTimeMinutes typical episode length; nullable
	 * @param nextEpisode next announced episode; nullable
	 * @param lastEpisode most recently aired episode; nullable
	 * @param seasons ascending by season number; season 0 holds specials
	 * @param cast billing order, at most {@value #MAX_CAST}
	 */
	public record ShowDetails(String ref, String name, String originalName, String overview, String tagline,
			ShowStatus status, LocalDate firstAirDate, LocalDate lastAirDate, String posterUrl, String backdropUrl,
			List<String> genres, List<String> networks, Integer episodeRunTimeMinutes, Integer numberOfSeasons,
			Integer numberOfEpisodes, Double voteAverage, List<SeasonSummary> seasons, EpisodeInfo nextEpisode,
			EpisodeInfo lastEpisode, List<CastMember> cast) {

		public static final int MAX_CAST = 20;

	}

	/**
	 * @param seasonNumber 0 for specials
	 * @param episodeCount nullable when the provider has not published it
	 */
	public record SeasonSummary(int seasonNumber, String name, Integer episodeCount, LocalDate airDate,
			String posterUrl) {
	}

	/** @param episodes ascending by episode number */
	public record SeasonDetails(String showRef, int seasonNumber, String name, String overview, LocalDate airDate,
			String posterUrl, List<EpisodeInfo> episodes) {
	}

	/**
	 * @param externalId provider's own episode id
	 * @param airDate nullable: unknown, never guessed
	 * @param runtimeMinutes nullable
	 */
	public record EpisodeInfo(String externalId, int seasonNumber, int episodeNumber, String name, String overview,
			LocalDate airDate, Integer runtimeMinutes, String stillUrl) {
	}

	/** @param character nullable; profileUrl nullable */
	public record CastMember(String name, String character, String profileUrl) {
	}

}
