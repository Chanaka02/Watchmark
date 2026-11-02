package app.watchmark.catalog.store;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GenreRepository extends JpaRepository<Genre, Long> {

	List<Genre> findByNameIn(Collection<String> names);

	/** Race-free insert: concurrent syncs may introduce the same genre. */
	@Modifying
	@Query(value = "INSERT INTO genres (name) VALUES (:name) ON DUPLICATE KEY UPDATE id = id", nativeQuery = true)
	void insertIfAbsent(@Param("name") String name);

}
