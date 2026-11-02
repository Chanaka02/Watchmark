package app.watchmark.library;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import app.watchmark.catalog.store.Show;

/** One user's library entry for one show. */
@Entity
@Table(name = "user_shows")
public class UserShow {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "show_id", nullable = false)
	private Show show;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LibraryStatus status;

	private Integer rating;

	@Column(columnDefinition = "text")
	private String notes;

	@Column(name = "current_cycle", nullable = false)
	private int currentCycle = 1;

	@Column(name = "added_at", nullable = false)
	private Instant addedAt;

	@Column(name = "removed_at")
	private Instant removedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected UserShow() {
	}

	UserShow(Long userId, Show show, LibraryStatus status, Instant now) {
		this.userId = userId;
		this.show = show;
		this.status = status;
		this.addedAt = now;
		this.updatedAt = now;
	}

	/** Sets the status; a removed entry is restored with its cycle, rating and notes (R10). */
	void changeStatus(LibraryStatus newStatus, Instant now) {
		status = newStatus;
		removedAt = null;
		updatedAt = now;
	}

	void rate(Integer newRating, Instant now) {
		rating = newRating;
		updatedAt = now;
	}

	void annotate(String newNotes, Instant now) {
		notes = newNotes;
		updatedAt = now;
	}

	/** A show first tracked without being in the library (R9). */
	public static UserShow startedByTracking(Long userId, Show show, Instant now) {
		return new UserShow(userId, show, LibraryStatus.WATCHING, now);
	}

	/**
	 * Called before episodes are marked watched (R9): restores a removed entry
	 * with its status, and moves {@code PLAN_TO_WATCH} to {@code WATCHING}.
	 */
	public void trackingStarted(Instant now) {
		removedAt = null;
		if (status == LibraryStatus.PLAN_TO_WATCH) {
			status = LibraryStatus.WATCHING;
		}
		updatedAt = now;
	}

	/**
	 * Status automation after any tracking change (R9). {@code ON_HOLD} and
	 * {@code DROPPED} are never changed, nor is a removed entry.
	 *
	 * @param cycleComplete every regular episode is watched in the current cycle
	 */
	public void reconcileStatus(boolean cycleComplete, Instant now) {
		if (isRemoved()) {
			return;
		}
		LibraryStatus next = status;
		if (cycleComplete && show.getStatus().isFinished()
				&& (status == LibraryStatus.WATCHING || status == LibraryStatus.PLAN_TO_WATCH)) {
			next = LibraryStatus.COMPLETED;
		}
		else if (!cycleComplete && status == LibraryStatus.COMPLETED) {
			next = LibraryStatus.WATCHING;
		}
		status = next;
		updatedAt = now;
	}

	/** Starts the next watch-through; earlier cycles' history is untouched. */
	public void startRewatch(Instant now) {
		currentCycle++;
		status = LibraryStatus.WATCHING;
		updatedAt = now;
	}

	/** Leaves the library; everything about the entry is kept (R10). */
	void remove(Instant now) {
		removedAt = now;
		updatedAt = now;
	}

	public boolean isRemoved() {
		return removedAt != null;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public Show getShow() {
		return show;
	}

	public LibraryStatus getStatus() {
		return status;
	}

	public Integer getRating() {
		return rating;
	}

	public String getNotes() {
		return notes;
	}

	public int getCurrentCycle() {
		return currentCycle;
	}

	public Instant getAddedAt() {
		return addedAt;
	}

	public Instant getRemovedAt() {
		return removedAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
