package app.watchmark.catalog.store;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import app.watchmark.catalog.ShowStatus;

interface ShowRepository extends JpaRepository<Show, Long> {

	Optional<Show> findByProviderAndExternalId(String provider, String externalId);

	/** Serialises concurrent syncs of the same show (row lock until commit). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Show s where s.provider = :provider and s.externalId = :externalId")
	Optional<Show> findForUpdate(@Param("provider") String provider, @Param("externalId") String externalId);

	/**
	 * Shows due for refresh, oldest first. A show is due when its data is older
	 * than the hard limit, or older than the limit for its kind: finished and
	 * upstream-missing shows change rarely, everything else may change daily.
	 * Shows attempted recently are skipped so failures are not retried in a loop.
	 */
	@Query("""
			select s.externalId from Show s
			where s.provider = :provider
			  and (s.lastRefreshAttemptAt is null or s.lastRefreshAttemptAt < :attemptedBefore)
			  and (s.metadataSyncedAt < :hardBefore
			       or ((s.status in :finished or s.upstreamMissingAt is not null)
			           and s.metadataSyncedAt < :finishedBefore)
			       or (s.status not in :finished and s.upstreamMissingAt is null
			           and s.metadataSyncedAt < :airingBefore))
			order by s.metadataSyncedAt asc
			""")
	List<String> findDueForRefresh(@Param("provider") String provider,
			@Param("finished") Collection<ShowStatus> finished, @Param("airingBefore") Instant airingBefore,
			@Param("finishedBefore") Instant finishedBefore, @Param("hardBefore") Instant hardBefore,
			@Param("attemptedBefore") Instant attemptedBefore, Pageable page);

	long countByMetadataSyncedAtBefore(Instant before);

}
