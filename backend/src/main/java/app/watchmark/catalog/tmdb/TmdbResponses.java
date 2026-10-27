package app.watchmark.catalog.tmdb;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Wire format of the TMDB v3 endpoints we call, following the field names in
 * TMDB's API reference (checked 2026-10-28). Only fields Watchmark uses are
 * declared; unknown fields are ignored. TMDB's schema marks nothing as
 * nullable, so every field here is treated as optional, and dates stay strings
 * because TMDB sends {@code ""} for unknown dates.
 */
final class TmdbResponses {

	private TmdbResponses() {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	record Page(Integer page, List<ShowListItem> results, Integer totalPages, Integer totalResults) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	record ShowListItem(Long id, String name, String originalName, String overview, String firstAirDate,
			String posterPath, Double voteAverage, Boolean adult) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	record ShowDetails(Long id, String name, String originalName, String overview, String tagline, String status,
			String firstAirDate, String lastAirDate, String posterPath, String backdropPath, List<Named> genres,
			List<Named> networks, List<Integer> episodeRunTime, Integer numberOfSeasons, Integer numberOfEpisodes,
			Double voteAverage, Boolean adult, List<SeasonSummary> seasons, Episode lastEpisodeToAir,
			Episode nextEpisodeToAir, Credits credits) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Named(Long id, String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	record SeasonSummary(Long id, Integer seasonNumber, String name, Integer episodeCount, String airDate,
			String posterPath) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	record Season(Long id, Integer seasonNumber, String name, String overview, String airDate, String posterPath,
			List<Episode> episodes) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	record Episode(Long id, Integer seasonNumber, Integer episodeNumber, String name, String overview,
			String airDate, Integer runtime, String stillPath) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Credits(List<Cast> cast) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	record Cast(Long id, String name, String character, String profilePath, Integer order) {
	}

}
