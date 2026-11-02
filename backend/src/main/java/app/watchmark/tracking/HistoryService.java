package app.watchmark.tracking;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.watchmark.catalog.store.Episode;
import app.watchmark.common.ApiException;
import app.watchmark.library.UserShow;
import app.watchmark.library.UserShowRepository;
import app.watchmark.library.dto.LibraryDtos.LibraryShow;
import app.watchmark.tracking.dto.TrackingDtos.HistoryEpisode;
import app.watchmark.tracking.dto.TrackingDtos.HistoryItem;
import app.watchmark.tracking.dto.TrackingDtos.HistoryPage;

/**
 * A user's viewing history, newest first: active watches of every cycle,
 * including shows removed from the library (R10). Soft-deleted rows are not
 * history. Paged by an opaque cursor over (watched_at, id), so pages stay
 * stable while new watches are added.
 */
@Service
public class HistoryService {

	private final EpisodeWatchRepository watches;

	private final UserShowRepository entries;

	HistoryService(EpisodeWatchRepository watches, UserShowRepository entries) {
		this.watches = watches;
		this.entries = entries;
	}

	/** @throws ApiException 400 {@code invalid-cursor} when the cursor was not issued by this API */
	@Transactional(readOnly = true)
	public HistoryPage page(long userId, String cursor, int size) {
		Limit limit = Limit.of(size + 1);
		List<EpisodeWatch> rows;
		if (cursor == null || cursor.isEmpty()) {
			rows = watches.findHistory(userId, limit);
		}
		else {
			Cursor position = Cursor.decode(cursor);
			rows = watches.findHistoryBefore(userId, position.watchedAt(), position.watchId(), limit);
		}
		boolean more = rows.size() > size;
		List<EpisodeWatch> page = more ? rows.subList(0, size) : rows;
		Set<Long> inLibrary = libraryShowIds(userId);
		String next = more ? Cursor.of(page.get(page.size() - 1)).encode() : null;
		return new HistoryPage(page.stream().map(row -> item(row, inLibrary)).toList(), next);
	}

	/** The latest viewings of shows that are in the library (for the dashboard). */
	@Transactional(readOnly = true)
	public List<HistoryItem> recentInLibrary(long userId, int limit) {
		return watches.findRecentInLibrary(userId, Limit.of(limit))
			.stream()
			.map(row -> item(row, null))
			.toList();
	}

	private Set<Long> libraryShowIds(long userId) {
		return entries.findLibrary(userId, null)
			.stream()
			.map(entry -> entry.getShow().getId())
			.collect(Collectors.toSet());
	}

	/** @param inLibrary show ids in the library, or {@code null} when all rows are known to be */
	private static HistoryItem item(EpisodeWatch row, Set<Long> inLibrary) {
		Episode episode = row.getEpisode();
		var show = episode.getShow();
		return new HistoryItem(row.getId(), row.getWatchedAt(), row.getCycle(),
				inLibrary == null || inLibrary.contains(show.getId()), LibraryShow.from(show),
				new HistoryEpisode(episode.getId(), episode.getSeasonNumber(), episode.getEpisodeNumber(),
						episode.getName(), episode.getRuntimeMinutes()));
	}

	/** Position after the last row of a page. Opaque to clients: base64url of {@code seconds.nanos:id}. */
	record Cursor(Instant watchedAt, long watchId) {

		static Cursor of(EpisodeWatch row) {
			return new Cursor(row.getWatchedAt(), row.getId());
		}

		String encode() {
			String raw = watchedAt.getEpochSecond() + "." + watchedAt.getNano() + ":" + watchId;
			return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.US_ASCII));
		}

		static Cursor decode(String cursor) {
			try {
				String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
				String[] parts = raw.split("[.:]", -1);
				if (parts.length != 3) {
					throw new IllegalArgumentException(raw);
				}
				return new Cursor(Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1])),
						Long.parseLong(parts[2]));
			}
			catch (IllegalArgumentException | DateTimeException | ArithmeticException ex) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-cursor", "Invalid cursor",
						"This history position is not valid. Start again from the first page.");
			}
		}

	}

}
