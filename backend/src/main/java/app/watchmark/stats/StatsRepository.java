package app.watchmark.stats;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import app.watchmark.catalog.store.Show;
import app.watchmark.tracking.EpisodeWatch;

/** Read-only queries for statistics. Viewing queries are scoped by user id (R1). */
interface StatsRepository extends Repository<EpisodeWatch, Long> {

	/** One active watch row: a viewing (R2). */
	interface Viewing {

		Long getEpisodeId();

		Instant getWatchedAt();

		/** {@code null} when the episode's runtime is unknown. */
		Integer getRuntime();

		Long getShowId();

	}

	interface ShowGenre {

		Long getShowId();

		String getName();

	}

	/** Active watches of every cycle and every show, removed ones included (R10), in {@code [from, until)}. */
	@Query("""
			select w.episodeId as episodeId, w.watchedAt as watchedAt, e.runtimeMinutes as runtime,
			       e.show.id as showId
			from EpisodeWatch w join w.episode e
			where w.userId = :userId and w.deletedAt is null and w.watchedAt >= :from and w.watchedAt < :until
			""")
	List<Viewing> findViewings(@Param("userId") Long userId, @Param("from") Instant from,
			@Param("until") Instant until);

	@Query("select s.id as showId, g.name as name from Show s join s.genres g where s.id in :showIds")
	List<ShowGenre> findGenres(@Param("showIds") Collection<Long> showIds);

	@Query("select s from Show s where s.id in :showIds")
	List<Show> findShows(@Param("showIds") Collection<Long> showIds);

}
