package app.watchmark.tracking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import app.watchmark.catalog.store.Episode;

/** Every query that reads or changes watch rows is scoped by user id (R1). */
interface EpisodeWatchRepository extends JpaRepository<EpisodeWatch, Long> {

	@Query("select e from Episode e join fetch e.show where e.id = :id")
	Optional<Episode> findEpisode(@Param("id") Long id);

	/** All stored episodes of a show, including upstream-missing ones (R8). */
	@Query("select e from Episode e where e.show.id = :showId")
	List<Episode> findEpisodesOfShow(@Param("showId") Long showId);

	@Query("""
			select w from EpisodeWatch w
			where w.userId = :userId and w.cycle = :cycle and w.deletedAt is null
			  and w.episodeId in (select e.id from Episode e where e.show.id = :showId)
			""")
	List<EpisodeWatch> findActiveInCycle(@Param("userId") Long userId, @Param("showId") Long showId,
			@Param("cycle") int cycle);

	@Query("""
			select w from EpisodeWatch w
			where w.userId = :userId and w.episodeId = :episodeId and w.cycle = :cycle and w.deletedAt is null
			""")
	Optional<EpisodeWatch> findActive(@Param("userId") Long userId, @Param("episodeId") Long episodeId,
			@Param("cycle") int cycle);

	@Query("""
			select count(w) > 0 from EpisodeWatch w
			where w.userId = :userId and w.cycle = :cycle and w.deletedAt is null and w.episodeId in :episodeIds
			""")
	boolean anyActive(@Param("userId") Long userId, @Param("cycle") int cycle,
			@Param("episodeIds") Collection<Long> episodeIds);

	@Query("select w from EpisodeWatch w where w.userId = :userId and w.createdByOp = :op and w.deletedAt is null")
	List<EpisodeWatch> findActiveCreatedBy(@Param("userId") Long userId, @Param("op") UUID op);

	@Query("select w from EpisodeWatch w where w.userId = :userId and w.deletedByOp = :op and w.deletedAt is not null")
	List<EpisodeWatch> findDeletedBy(@Param("userId") Long userId, @Param("op") UUID op);

	// --- History (newest first; ties on watched_at broken by id, for stable cursors) ---------------

	@Query("""
			select w from EpisodeWatch w join fetch w.episode e join fetch e.show
			where w.userId = :userId and w.deletedAt is null
			order by w.watchedAt desc, w.id desc
			""")
	List<EpisodeWatch> findHistory(@Param("userId") Long userId, Limit limit);

	@Query("""
			select w from EpisodeWatch w join fetch w.episode e join fetch e.show
			where w.userId = :userId and w.deletedAt is null
			  and (w.watchedAt < :at or (w.watchedAt = :at and w.id < :id))
			order by w.watchedAt desc, w.id desc
			""")
	List<EpisodeWatch> findHistoryBefore(@Param("userId") Long userId, @Param("at") Instant at, @Param("id") Long id,
			Limit limit);

	/** Like {@link #findHistory}, limited to shows currently in the user's library (R10). */
	@Query("""
			select w from EpisodeWatch w join fetch w.episode e join fetch e.show s
			where w.userId = :userId and w.deletedAt is null
			  and exists (select 1 from UserShow us
			              where us.userId = :userId and us.show = s and us.removedAt is null)
			order by w.watchedAt desc, w.id desc
			""")
	List<EpisodeWatch> findRecentInLibrary(@Param("userId") Long userId, Limit limit);

	// --- Several shows at once (dashboard) ---------------------------------------------------------

	@Query("select e from Episode e where e.show.id in :showIds")
	List<Episode> findEpisodesOfShows(@Param("showIds") Collection<Long> showIds);

	@Query("""
			select w from EpisodeWatch w join fetch w.episode e
			where w.userId = :userId and w.deletedAt is null and e.show.id in :showIds
			""")
	List<EpisodeWatch> findActiveForShows(@Param("userId") Long userId, @Param("showIds") Collection<Long> showIds);

	@Query("""
			select w from EpisodeWatch w join fetch w.episode e
			where w.userId = :userId and w.deletedAt is null and w.episodeId in :episodeIds
			""")
	List<EpisodeWatch> findActiveForEpisodes(@Param("userId") Long userId,
			@Param("episodeIds") Collection<Long> episodeIds);

	@Query("""
			select distinct e.show.id from Episode e
			where e.show.id in :showIds and e.airDate is null and e.upstreamMissingAt is null
			""")
	Set<Long> findShowsWithUndatedEpisodes(@Param("showIds") Collection<Long> showIds);

	/** Episodes still listed upstream with an air date in {@code [from, to]}. */
	@Query("""
			select e from Episode e join fetch e.show s
			where s.id in :showIds and e.upstreamMissingAt is null and e.airDate between :from and :to
			order by e.airDate, s.name, e.seasonNumber, e.episodeNumber
			""")
	List<Episode> findAiringBetween(@Param("showIds") Collection<Long> showIds, @Param("from") LocalDate from,
			@Param("to") LocalDate to);

	// --- Counts (R2): active rows of every cycle, removed shows included (R10) ---------------------

	@Query("select count(distinct w.episodeId) from EpisodeWatch w where w.userId = :userId and w.deletedAt is null")
	long countUniqueEpisodes(@Param("userId") Long userId);

	@Query("select count(w) from EpisodeWatch w where w.userId = :userId and w.deletedAt is null")
	long countWatches(@Param("userId") Long userId);

	@Query("""
			select count(distinct w.episodeId) from EpisodeWatch w
			where w.userId = :userId and w.deletedAt is null
			  and w.episodeId in (select e.id from Episode e where e.show.id = :showId)
			""")
	long countUniqueEpisodes(@Param("userId") Long userId, @Param("showId") Long showId);

	@Query("""
			select count(w) from EpisodeWatch w
			where w.userId = :userId and w.deletedAt is null
			  and w.episodeId in (select e.id from Episode e where e.show.id = :showId)
			""")
	long countWatches(@Param("userId") Long userId, @Param("showId") Long showId);

	// --- Permanent deletion (R11, R12) -------------------------------------------------------------

	@Modifying
	@Query(value = """
			DELETE w FROM episode_watches w JOIN episodes e ON e.id = w.episode_id
			WHERE w.user_id = :userId AND e.show_id = :showId
			""", nativeQuery = true)
	int deleteForShow(@Param("userId") Long userId, @Param("showId") Long showId);

	@Modifying
	@Query(value = "DELETE FROM episode_watches WHERE user_id = :userId", nativeQuery = true)
	int deleteForUser(@Param("userId") Long userId);

	/** R12: soft-deleted rows whose deletion is older than the cutoff. */
	@Modifying
	@Query(value = "DELETE FROM episode_watches WHERE deleted_at < :cutoff", nativeQuery = true)
	int purgeDeletedBefore(@Param("cutoff") Instant cutoff);

}
