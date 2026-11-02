package app.watchmark.library;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query is scoped by user id: one user can never read another user's entries (R1). */
public interface UserShowRepository extends JpaRepository<UserShow, Long> {

	@Query("""
			select us from UserShow us join fetch us.show s
			where us.userId = :userId and s.provider = :provider and s.externalId = :externalId
			""")
	Optional<UserShow> findEntry(@Param("userId") Long userId, @Param("provider") String provider,
			@Param("externalId") String externalId);

	/**
	 * Locks the user's entry for a show until commit, so concurrent tracking
	 * requests for the same user and show run one after another. The show itself
	 * is deliberately not fetched here: that would lock the shared show row too.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select us from UserShow us where us.userId = :userId and us.show.id = :showId")
	Optional<UserShow> findForUpdate(@Param("userId") Long userId, @Param("showId") Long showId);

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
