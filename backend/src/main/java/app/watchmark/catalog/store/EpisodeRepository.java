package app.watchmark.catalog.store;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EpisodeRepository extends JpaRepository<Episode, Long> {

	@Query("select e from Episode e where e.show.id = :showId order by e.seasonNumber, e.episodeNumber, e.id")
	List<Episode> findByShowId(@Param("showId") Long showId);

}
