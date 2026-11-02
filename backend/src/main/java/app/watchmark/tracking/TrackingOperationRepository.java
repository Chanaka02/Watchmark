package app.watchmark.tracking;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query that reads operations is scoped by user id (R1). */
interface TrackingOperationRepository extends JpaRepository<TrackingOperation, UUID> {

	/** Locks the operation until commit (undo checks and sets {@code undone_at}). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<TrackingOperation> findByIdAndUserId(UUID id, Long userId);

	@Modifying
	@Query(value = "DELETE FROM tracking_operations WHERE user_id = :userId AND show_id = :showId", nativeQuery = true)
	int deleteForShow(@Param("userId") Long userId, @Param("showId") Long showId);

	@Modifying
	@Query(value = "DELETE FROM tracking_operations WHERE user_id = :userId", nativeQuery = true)
	int deleteForUser(@Param("userId") Long userId);

	/**
	 * R12: operations older than the cutoff that no watch row references any
	 * more, either as creator or as the unwatch that deleted it.
	 */
	@Modifying
	@Query(value = """
			DELETE FROM tracking_operations
			WHERE created_at < :cutoff
			  AND NOT EXISTS (SELECT 1 FROM episode_watches w
			                  WHERE w.created_by_op = tracking_operations.id OR w.deleted_by_op = tracking_operations.id)
			""", nativeQuery = true)
	int purgeUnreferencedBefore(@Param("cutoff") Instant cutoff);

}
