package app.watchmark.tracking;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import app.watchmark.catalog.store.Episode;

/**
 * One viewing of an episode in one cycle (rule R2). Unwatching soft-deletes the
 * row so undo can restore it (R3). The generated {@code active_marker} column is
 * not mapped: only the database uses it, to enforce one active row per user,
 * episode and cycle (R4).
 */
@Entity
@Table(name = "episode_watches")
public class EpisodeWatch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "episode_id", nullable = false)
	private Long episodeId;

	/** Read-only view of {@link #episodeId}, for queries that need the episode and its show. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "episode_id", insertable = false, updatable = false)
	private Episode episode;

	@Column(nullable = false)
	private int cycle;

	@Column(name = "watched_at", nullable = false)
	private Instant watchedAt;

	@Column(name = "created_by_op", nullable = false)
	private UUID createdByOp;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "deleted_by_op")
	private UUID deletedByOp;

	protected EpisodeWatch() {
	}

	EpisodeWatch(Long userId, Long episodeId, int cycle, UUID createdByOp, Instant now) {
		this.userId = userId;
		this.episodeId = episodeId;
		this.cycle = cycle;
		this.createdByOp = createdByOp;
		this.watchedAt = now;
	}

	/** @param operation the unwatch operation, or {@code null} when undoing a watch */
	void softDelete(UUID operation, Instant now) {
		deletedAt = now;
		deletedByOp = operation;
	}

	/** Undo of an unwatch: active again, with its original watch time. */
	void restore() {
		deletedAt = null;
		deletedByOp = null;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public Long getEpisodeId() {
		return episodeId;
	}

	public Episode getEpisode() {
		return episode;
	}

	public int getCycle() {
		return cycle;
	}

	public Instant getWatchedAt() {
		return watchedAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
