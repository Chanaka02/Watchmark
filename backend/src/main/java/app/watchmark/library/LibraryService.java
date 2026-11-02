package app.watchmark.library;

import java.time.Clock;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import app.watchmark.catalog.ShowRef;
import app.watchmark.catalog.store.MetadataSyncService;
import app.watchmark.catalog.store.Show;
import app.watchmark.common.ApiException;
import app.watchmark.library.dto.LibraryDtos.LibraryEntry;
import app.watchmark.library.dto.LibraryDtos.LibraryPage;

/**
 * A user's library (rules R1, R10, R11 in docs/tracking-rules.md). Every method
 * takes the user id from the caller's session; nothing here accepts one from a
 * request.
 */
@Service
public class LibraryService {

	public enum Sort {

		/** Most recently changed first (default). */
		RECENT,
		/** Alphabetical by show name. */
		TITLE,
		/** Most recently added first. */
		ADDED

	}

	private final UserShowRepository entries;

	private final MetadataSyncService metadata;

	private final EntityManager entityManager;

	private final TransactionTemplate transaction;

	private final Clock clock;

	LibraryService(UserShowRepository entries, MetadataSyncService metadata, EntityManager entityManager,
			TransactionTemplate transaction, Clock clock) {
		this.entries = entries;
		this.metadata = metadata;
		this.entityManager = entityManager;
		this.transaction = transaction;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public LibraryPage list(long userId, LibraryStatus status, Sort sort) {
		Comparator<UserShow> order = switch (sort) {
			case RECENT -> Comparator.comparing(UserShow::getUpdatedAt).reversed();
			case ADDED -> Comparator.comparing(UserShow::getAddedAt).reversed();
			case TITLE -> Comparator.comparing(entry -> entry.getShow().getName().toLowerCase(Locale.ROOT));
		};
		List<LibraryEntry> page = entries.findLibrary(userId, status)
			.stream()
			.sorted(order.thenComparing(UserShow::getId))
			.map(LibraryEntry::from)
			.toList();
		Map<LibraryStatus, Long> counts = new EnumMap<>(LibraryStatus.class);
		for (LibraryStatus each : LibraryStatus.values()) {
			counts.put(each, 0L);
		}
		entries.countByStatus(userId).forEach(count -> counts.put(count.getStatus(), count.getCount()));
		return new LibraryPage(page, counts);
	}

	/** @throws ApiException 404 {@code not-in-library} when absent or removed */
	@Transactional(readOnly = true)
	public LibraryEntry get(long userId, ShowRef ref) {
		return LibraryEntry.from(activeEntry(userId, ref));
	}

	public record StatusResult(LibraryEntry entry, boolean created) {
	}

	/**
	 * Adds the show, changes its status, or restores a removed entry with its
	 * previous cycle, rating and notes (R10). Stores the show's metadata first if
	 * no user has added it before; that provider call happens outside the
	 * transaction.
	 */
	public StatusResult setStatus(long userId, ShowRef ref, LibraryStatus status) {
		long showId = metadata.ensureStored(ref);
		try {
			return transaction.execute(tx -> upsertStatus(userId, showId, ref, status));
		}
		catch (DataIntegrityViolationException | PessimisticLockingFailureException ex) {
			// The same user added the same show concurrently (two tabs): the other insert won,
			// either by duplicate key or by MySQL choosing this one as deadlock victim.
			return transaction.execute(tx -> upsertStatus(userId, showId, ref, status));
		}
	}

	@Transactional
	public LibraryEntry setRating(long userId, ShowRef ref, Integer rating) {
		UserShow entry = activeEntry(userId, ref);
		entry.rate(rating, clock.instant());
		return LibraryEntry.from(entry);
	}

	@Transactional
	public LibraryEntry setNotes(long userId, ShowRef ref, String notes) {
		UserShow entry = activeEntry(userId, ref);
		entry.annotate(notes == null || notes.isBlank() ? null : notes.strip(), clock.instant());
		return LibraryEntry.from(entry);
	}

	/** R10: leaves the library; history, rating, notes and cycle are kept. */
	@Transactional
	public void remove(long userId, ShowRef ref) {
		activeEntry(userId, ref).remove(clock.instant());
	}

	/**
	 * R11: permanently deletes this user's data for the show, whether or not it
	 * is currently in the library. Requires the show name as displayed
	 * (case and surrounding whitespace ignored). Cannot be undone.
	 */
	@Transactional
	public void purge(long userId, ShowRef ref, String confirmName) {
		UserShow entry = entries.findEntry(userId, ref.provider(), ref.externalId())
			.orElseThrow(LibraryService::notInLibrary);
		if (!normalise(confirmName).equals(normalise(entry.getShow().getName()))) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "confirmation-mismatch",
					"Confirmation does not match", "Type the show's name exactly as shown to confirm.");
		}
		// Milestone 5 also deletes this user's watch rows and tracking operations for the show here.
		entries.delete(entry);
	}

	private StatusResult upsertStatus(long userId, long showId, ShowRef ref, LibraryStatus status) {
		var existing = entries.findEntry(userId, ref.provider(), ref.externalId());
		if (existing.isPresent()) {
			UserShow entry = existing.get();
			entry.changeStatus(status, clock.instant());
			entries.flush();
			return new StatusResult(LibraryEntry.from(entry), false);
		}
		Show show = entityManager.getReference(Show.class, showId);
		UserShow created = entries.saveAndFlush(new UserShow(userId, show, status, clock.instant()));
		// Reload with the show loaded, for the response.
		return new StatusResult(LibraryEntry.from(entries.findEntry(userId, ref.provider(), ref.externalId())
			.orElse(created)), true);
	}

	private UserShow activeEntry(long userId, ShowRef ref) {
		return entries.findEntry(userId, ref.provider(), ref.externalId())
			.filter(entry -> !entry.isRemoved())
			.orElseThrow(LibraryService::notInLibrary);
	}

	private static ApiException notInLibrary() {
		return new ApiException(HttpStatus.NOT_FOUND, "not-in-library", "Not in library",
				"This show is not in your library.");
	}

	private static String normalise(String name) {
		return name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

}
