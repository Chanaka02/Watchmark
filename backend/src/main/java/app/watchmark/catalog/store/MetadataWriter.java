package app.watchmark.catalog.store;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import app.watchmark.catalog.ShowRef;
import app.watchmark.catalog.dto.CatalogDtos.EpisodeInfo;
import app.watchmark.catalog.dto.CatalogDtos.SeasonDetails;
import app.watchmark.catalog.dto.CatalogDtos.SeasonSummary;
import app.watchmark.catalog.dto.CatalogDtos.ShowDetails;

/**
 * Applies already-fetched provider data to the database in one transaction.
 * Kept separate from {@link MetadataSyncService} so no network call ever runs
 * while a transaction (and a pooled connection plus row locks) is open.
 *
 * <p>
 * Invariants:
 * <ul>
 * <li>Rows are never deleted. Seasons and episodes the provider no longer lists
 * get {@code upstream_missing_at} (rule R8); they reactivate if they reappear.</li>
 * <li>Episodes are matched by the provider's episode id, so renumbering keeps
 * the same row (and, later, the watch history that references it).</li>
 * <li>Only seasons whose episodes were fetched are reconciled episode by episode;
 * other seasons keep their stored episodes.</li>
 * </ul>
 */
@Component
class MetadataWriter {

	private final ShowRepository shows;

	private final SeasonRepository seasons;

	private final EpisodeRepository episodes;

	private final GenreRepository genres;

	private final EntityManager entityManager;

	MetadataWriter(ShowRepository shows, SeasonRepository seasons, EpisodeRepository episodes,
			GenreRepository genres, EntityManager entityManager) {
		this.shows = shows;
		this.seasons = seasons;
		this.episodes = episodes;
		this.genres = genres;
		this.entityManager = entityManager;
	}

	/** What is stored per season, to decide which seasons need their episodes fetched. */
	record SeasonState(Integer episodeCount, Instant episodesSyncedAt, boolean hasPendingEpisodes) {
	}

	/**
	 * Active stored seasons by number; empty when the show is not stored yet.
	 * @param today episodes airing on or after the day before {@code today}, or
	 *     with no date, count as pending (they may still change upstream)
	 */
	@Transactional(readOnly = true)
	Map<Integer, SeasonState> seasonStates(ShowRef ref, LocalDate today) {
		Optional<Show> show = shows.findByProviderAndExternalId(ref.provider(), ref.externalId());
		if (show.isEmpty()) {
			return Map.of();
		}
		LocalDate pendingFrom = today.minusDays(1);
		Map<Long, Boolean> pendingBySeason = episodes.findByShowId(show.get().getId())
			.stream()
			.filter(Episode::isActive)
			.collect(Collectors.groupingBy(Episode::getSeasonId, Collectors.reducing(false,
					episode -> episode.getAirDate() == null || !episode.getAirDate().isBefore(pendingFrom),
					Boolean::logicalOr)));
		Map<Integer, SeasonState> states = new HashMap<>();
		for (Season season : seasons.findByShowId(show.get().getId())) {
			if (season.isActive()) {
				states.put(season.getSeasonNumber(), new SeasonState(season.getEpisodeCount(),
						season.getEpisodesSyncedAt(), pendingBySeason.getOrDefault(season.getId(), false)));
			}
		}
		return states;
	}

	/**
	 * @param fetched seasons whose episode lists were fetched in this sync
	 * @throws org.springframework.dao.DataIntegrityViolationException when another
	 *     sync created the same show concurrently; the caller retries once
	 */
	@Transactional
	SyncOutcome apply(ShowRef ref, ShowDetails details, List<SeasonDetails> fetched, Instant now) {
		Show show = shows.findForUpdate(ref.provider(), ref.externalId()).orElse(null);
		boolean created = show == null;
		if (created) {
			show = shows.saveAndFlush(Show.create(ref, details, now));
		}
		show.apply(details, resolveGenres(details.genres()), now);

		Map<Integer, Season> activeSeasons = reconcileSeasons(show, details.seasons(), now);
		List<Episode> showEpisodes = new ArrayList<>(episodes.findByShowId(show.getId()));
		var counts = new EpisodeCounts();
		for (SeasonDetails seasonDetails : fetched) {
			Season season = activeSeasons.get(seasonDetails.seasonNumber());
			if (season != null) {
				reconcileEpisodes(show, season, seasonDetails.episodes(), showEpisodes, now, counts);
				season.episodesSynced(now);
			}
		}
		entityManager.flush();
		return new SyncOutcome(show.getId(), created, false, fetched.size(), counts.added, counts.updated,
				counts.missing);
	}

	@Transactional
	void recordFailure(ShowRef ref, Instant now) {
		shows.findForUpdate(ref.provider(), ref.externalId()).ifPresent(show -> show.recordRefreshFailure(now));
	}

	/** @return the stored show id, or empty if the show was never stored */
	@Transactional
	Optional<Long> markShowMissing(ShowRef ref, Instant now) {
		return shows.findForUpdate(ref.provider(), ref.externalId()).map(show -> {
			show.markUpstreamMissing(now);
			return show.getId();
		});
	}

	private Set<Genre> resolveGenres(List<String> names) {
		Set<String> wanted = names.stream()
			.filter(Objects::nonNull)
			.map(String::strip)
			.filter(name -> !name.isEmpty() && name.length() <= Genre.MAX_NAME)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		if (wanted.isEmpty()) {
			return Set.of();
		}
		wanted.forEach(genres::insertIfAbsent);
		return new HashSet<>(genres.findByNameIn(wanted));
	}

	/** @return the active season for each season number the provider lists */
	private Map<Integer, Season> reconcileSeasons(Show show, List<SeasonSummary> upstream, Instant now) {
		List<Season> stored = seasons.findByShowId(show.getId());
		Map<Integer, Season> active = new HashMap<>();
		Map<Integer, Season> latestMissing = new HashMap<>();
		for (Season season : stored) {
			if (season.isActive()) {
				active.put(season.getSeasonNumber(), season);
			}
			else {
				latestMissing.merge(season.getSeasonNumber(), season,
						(a, b) -> a.getUpstreamMissingAt().isAfter(b.getUpstreamMissingAt()) ? a : b);
			}
		}

		Map<Integer, Season> result = new LinkedHashMap<>();
		Set<Integer> listed = new HashSet<>();
		for (SeasonSummary summary : upstream) {
			if (!listed.add(summary.seasonNumber())) {
				continue; // provider listed the same season twice; keep the first
			}
			Season season = active.get(summary.seasonNumber());
			if (season == null) {
				season = latestMissing.get(summary.seasonNumber()); // reappeared: revive the old row
			}
			if (season == null) {
				// Populate before saving: IDENTITY ids make save() insert immediately.
				Season created = new Season(show, summary.seasonNumber());
				created.apply(summary);
				season = seasons.save(created);
			}
			else {
				season.apply(summary);
			}
			result.put(summary.seasonNumber(), season);
		}

		for (Season season : active.values()) {
			if (!listed.contains(season.getSeasonNumber())) {
				season.markUpstreamMissing(now);
				// Its episodes are no longer listed anywhere upstream either.
				episodes.findByShowId(show.getId())
					.stream()
					.filter(episode -> episode.isActive() && episode.belongsTo(season))
					.forEach(episode -> episode.markUpstreamMissing(now));
			}
		}
		entityManager.flush();
		return result;
	}

	/**
	 * Three flushed phases, because MySQL checks the unique key on every row
	 * write and Hibernate orders inserts before updates within one flush:
	 * <ol>
	 * <li>mark dropped episodes missing, and take renumbered ones out of the
	 * unique key temporarily (also by marking them missing);</li>
	 * <li>move matched episodes to their new numbers and reactivate them;</li>
	 * <li>insert new episodes.</li>
	 * </ol>
	 */
	private void reconcileEpisodes(Show show, Season season, List<EpisodeInfo> upstream, List<Episode> showEpisodes,
			Instant now, EpisodeCounts counts) {
		// One entry per episode number; providers occasionally repeat entries.
		Map<Integer, EpisodeInfo> byNumber = new LinkedHashMap<>();
		upstream.stream()
			.sorted(Comparator.comparingInt(EpisodeInfo::episodeNumber))
			.forEach(info -> byNumber.putIfAbsent(info.episodeNumber(), info));

		Map<String, Episode> byExternalId = new HashMap<>();
		for (Episode episode : showEpisodes) {
			if (episode.getExternalId() != null) {
				byExternalId.put(episode.getExternalId(), episode);
			}
		}

		Map<EpisodeInfo, Episode> matches = new LinkedHashMap<>();
		for (EpisodeInfo info : byNumber.values()) {
			Episode match = info.externalId() == null ? null : byExternalId.get(info.externalId());
			if (match == null) {
				// No provider id to go on: reuse an active row in the same slot that has none either.
				match = showEpisodes.stream()
					.filter(episode -> episode.isActive() && episode.getExternalId() == null
							&& episode.occupies(season.getSeasonNumber(), info.episodeNumber()))
					.findFirst()
					.orElse(null);
			}
			if (match != null && matches.containsValue(match)) {
				match = null;
			}
			matches.put(info, match);
		}

		// Phase 1.
		Set<Episode> matched = new HashSet<>();
		matches.values().stream().filter(Objects::nonNull).forEach(matched::add);
		for (Episode episode : showEpisodes) {
			if (episode.isActive() && episode.belongsTo(season) && !matched.contains(episode)) {
				episode.markUpstreamMissing(now);
				counts.missing++;
			}
		}
		matches.forEach((info, episode) -> {
			if (episode != null && episode.isActive()
					&& !episode.occupies(season.getSeasonNumber(), info.episodeNumber())) {
				episode.markUpstreamMissing(now);
			}
		});
		entityManager.flush();

		// Phase 2.
		matches.forEach((info, episode) -> {
			if (episode != null) {
				episode.apply(season, info);
				counts.updated++;
			}
		});
		entityManager.flush();

		// Phase 3.
		matches.forEach((info, episode) -> {
			if (episode == null) {
				Episode created = episodes.save(new Episode(show, season, info));
				showEpisodes.add(created);
				counts.added++;
			}
		});
		entityManager.flush();
	}

	private static final class EpisodeCounts {

		int added;

		int updated;

		int missing;

	}

}
