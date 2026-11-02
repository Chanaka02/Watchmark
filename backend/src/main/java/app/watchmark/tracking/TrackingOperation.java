package app.watchmark.tracking;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/** One undoable tracking action (rule R3). */
@Entity
@Table(name = "tracking_operations")
public class TrackingOperation {

	/** Time-ordered (version 7), so new rows append to the primary key index. */
	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "show_id", nullable = false)
	private Long showId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private OperationType type;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "undone_at")
	private Instant undoneAt;

	protected TrackingOperation() {
	}

	TrackingOperation(Long userId, Long showId, OperationType type, Instant now) {
		this.userId = userId;
		this.showId = showId;
		this.type = type;
		this.createdAt = now;
	}

	void undone(Instant now) {
		undoneAt = now;
	}

	boolean isUndone() {
		return undoneAt != null;
	}

	public UUID getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public Long getShowId() {
		return showId;
	}

	public OperationType getType() {
		return type;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUndoneAt() {
		return undoneAt;
	}

}
