package app.watchmark.library;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query is scoped by user id: one user can never read another user's entries (R1). */
interface UserShowRepository extends JpaRepository<UserShow, Long> {

	@Query("""
			select us from UserShow us join fetch us.show s
			where us.userId = :userId and s.provider = :provider and s.externalId = :externalId
			""")
	Optional<UserShow> findEntry(@Param("userId") Long userId, @Param("provider") String provider,
			@Param("externalId") String externalId);

	@Query("""
			select us from UserShow us join fetch us.show
			where us.userId = :userId and us.removedAt is null
			  and (:status is null or us.status = :status)
			""")
	List<UserShow> findLibrary(@Param("userId") Long userId, @Param("status") LibraryStatus status);

	interface StatusCount {

		LibraryStatus getStatus();

		long getCount();

	}

	@Query("""
			select us.status as status, count(us) as count from UserShow us
			where us.userId = :userId and us.removedAt is null
			group by us.status
			""")
	List<StatusCount> countByStatus(@Param("userId") Long userId);

}
