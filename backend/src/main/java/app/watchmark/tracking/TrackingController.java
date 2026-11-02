package app.watchmark.tracking;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.watchmark.auth.WatchmarkUserDetails;
import app.watchmark.catalog.MetadataNotFoundException;
import app.watchmark.catalog.ShowRef;
import app.watchmark.tracking.dto.TrackingDtos.UndoResult;
import app.watchmark.tracking.dto.TrackingDtos.UnwatchResult;
import app.watchmark.tracking.dto.TrackingDtos.WatchResult;

/** Episode tracking for the signed-in user. The user always comes from the session (R1). */
@RestController
@RequestMapping("/api/v1/tracking")
class TrackingController {

	private final TrackingService tracking;

	TrackingController(TrackingService tracking) {
		this.tracking = tracking;
	}

	@PutMapping("/episodes/{episodeId}/watch")
	WatchResult watch(@AuthenticationPrincipal WatchmarkUserDetails principal, @PathVariable long episodeId) {
		return tracking.watch(principal.id(), episodeId);
	}

	@DeleteMapping("/episodes/{episodeId}/watch")
	UnwatchResult unwatch(@AuthenticationPrincipal WatchmarkUserDetails principal, @PathVariable long episodeId) {
		return tracking.unwatch(principal.id(), episodeId);
	}

	@PostMapping("/episodes/{episodeId}/watch-up-to")
	WatchResult watchUpTo(@AuthenticationPrincipal WatchmarkUserDetails principal, @PathVariable long episodeId) {
		return tracking.watchUpTo(principal.id(), episodeId);
	}

	@PostMapping("/shows/{showRef}/seasons/{seasonNumber}/watch")
	WatchResult watchSeason(@AuthenticationPrincipal WatchmarkUserDetails principal, @PathVariable String showRef,
			@PathVariable int seasonNumber) {
		ShowRef ref = ShowRef.parse(showRef).orElseThrow(MetadataNotFoundException::show);
		return tracking.watchSeason(principal.id(), ref, seasonNumber);
	}

	/** R3: undo within the undo window. */
	@PostMapping("/operations/{operationId}/undo")
	UndoResult undo(@AuthenticationPrincipal WatchmarkUserDetails principal, @PathVariable String operationId) {
		UUID id;
		try {
			id = UUID.fromString(operationId);
		}
		catch (IllegalArgumentException ex) {
			throw TrackingService.operationNotFound();
		}
		return tracking.undo(principal.id(), id);
	}

}
