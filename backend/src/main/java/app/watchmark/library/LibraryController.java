package app.watchmark.library;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import app.watchmark.auth.WatchmarkUserDetails;
import app.watchmark.catalog.MetadataNotFoundException;
import app.watchmark.catalog.ShowRef;
import app.watchmark.library.dto.LibraryDtos.LibraryEntry;
import app.watchmark.library.dto.LibraryDtos.LibraryPage;
import app.watchmark.library.dto.LibraryDtos.PurgeRequest;
import app.watchmark.library.dto.LibraryDtos.SetNotesRequest;
import app.watchmark.library.dto.LibraryDtos.SetRatingRequest;
import app.watchmark.library.dto.LibraryDtos.SetStatusRequest;

/** The signed-in user's library. The user always comes from the session (R1). */
@RestController
@RequestMapping("/api/v1/library")
class LibraryController {

	private final LibraryService library;

	LibraryController(LibraryService library) {
		this.library = library;
	}

	@GetMapping
	LibraryPage list(@AuthenticationPrincipal WatchmarkUserDetails principal,
			@RequestParam(required = false) LibraryStatus status,
			@RequestParam(defaultValue = "RECENT") LibraryService.Sort sort) {
		return library.list(principal.id(), status, sort);
	}

	@GetMapping("/{showRef}")
	LibraryEntry get(@AuthenticationPrincipal WatchmarkUserDetails principal, @PathVariable String showRef) {
		return library.get(principal.id(), parse(showRef));
	}

	/** Adds (201), restores or changes status (200). */
	@PutMapping("/{showRef}")
	ResponseEntity<LibraryEntry> setStatus(@AuthenticationPrincipal WatchmarkUserDetails principal,
			@PathVariable String showRef, @Valid @RequestBody SetStatusRequest body) {
		var result = library.setStatus(principal.id(), parse(showRef), body.status());
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.entry());
	}

	@PutMapping("/{showRef}/rating")
	LibraryEntry setRating(@AuthenticationPrincipal WatchmarkUserDetails principal, @PathVariable String showRef,
			@Valid @RequestBody SetRatingRequest body) {
		return library.setRating(principal.id(), parse(showRef), body.rating());
	}

	@PutMapping("/{showRef}/notes")
	LibraryEntry setNotes(@AuthenticationPrincipal WatchmarkUserDetails principal, @PathVariable String showRef,
			@Valid @RequestBody SetNotesRequest body) {
		return library.setNotes(principal.id(), parse(showRef), body.notes());
	}

	/** R10: removes from the library, keeping history. */
	@DeleteMapping("/{showRef}")
	ResponseEntity<Void> remove(@AuthenticationPrincipal WatchmarkUserDetails principal,
			@PathVariable String showRef) {
		library.remove(principal.id(), parse(showRef));
		return ResponseEntity.noContent().build();
	}

	/** R11: permanently deletes this user's data for the show. */
	@PostMapping("/{showRef}/purge")
	ResponseEntity<Void> purge(@AuthenticationPrincipal WatchmarkUserDetails principal, @PathVariable String showRef,
			@Valid @RequestBody PurgeRequest body) {
		library.purge(principal.id(), parse(showRef), body.confirmName());
		return ResponseEntity.noContent().build();
	}

	private static ShowRef parse(String showRef) {
		return ShowRef.parse(showRef).orElseThrow(MetadataNotFoundException::show);
	}

}
