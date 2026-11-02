package app.watchmark.library.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import app.watchmark.catalog.ShowStatus;
import app.watchmark.catalog.store.Show;
import app.watchmark.library.LibraryStatus;
import app.watchmark.library.UserShow;

/** Library API contract. */
public final class LibraryDtos {

	public static final int MAX_NOTES = 2000;

	private LibraryDtos() {
	}

	/** The parts of stored show metadata a library card needs. */
	public record LibraryShow(String ref, String name, String posterUrl, LocalDate firstAirDate, ShowStatus status) {

		public static LibraryShow from(Show show) {
			return new LibraryShow(show.ref().toString(), show.getName(), show.getPosterUrl(), show.getFirstAirDate(),
					show.getStatus());
		}

	}

	/**
	 * @param rating nullable, 1–10
	 * @param notes nullable
	 * @param cycle 1 = first watch-through, 2+ = rewatch
	 */
	public record LibraryEntry(LibraryShow show, LibraryStatus status, Integer rating, String notes, int cycle,
			Instant addedAt, Instant updatedAt) {

		public static LibraryEntry from(UserShow entry) {
			return new LibraryEntry(LibraryShow.from(entry.getShow()), entry.getStatus(), entry.getRating(),
					entry.getNotes(), entry.getCurrentCycle(), entry.getAddedAt(), entry.getUpdatedAt());
		}

	}

	/** @param counts active entries per status, every status present (0 when none) */
	public record LibraryPage(List<LibraryEntry> entries, Map<LibraryStatus, Long> counts) {
	}

	public record SetStatusRequest(@NotNull LibraryStatus status) {
	}

	/** {@code null} clears the rating. */
	public record SetRatingRequest(@Min(1) @Max(10) Integer rating) {
	}

	/** {@code null} or blank clears the notes. */
	public record SetNotesRequest(@Size(max = MAX_NOTES) String notes) {
	}

	/** The show's name exactly as displayed, to confirm an irreversible deletion (R11). */
	public record PurgeRequest(@NotBlank @Size(max = 300) String confirmName) {
	}

}
