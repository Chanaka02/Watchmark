package app.watchmark.catalog.store;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SeasonRepository extends JpaRepository<Season, Long> {

	@Query("select s from Season s where s.show.id = :showId order by s.seasonNumber, s.id")
	List<Season> findByShowId(@Param("showId") Long showId);

}
