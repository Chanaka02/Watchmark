package app.watchmark.tracking;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.watchmark.auth.WatchmarkUserDetails;
import app.watchmark.catalog.MetadataNotFoundException;
import app.watchmark.catalog.ShowRef;
import app.watchmark.library.dto.LibraryDtos.LibraryEntry;
import app.watchmark.tracking.dto.TrackingDtos.SeasonProgress;
import app.watchmark.tracking.dto.TrackingDtos.ShowProgress;

/** Progress and rewatch of a show in the signed-in user's library (R1, R6, R7). */
@RestController
@RequestMapping("/api/v1/library/{showRef}")
class ProgressController {

	private final ProgressService progress;

	ProgressController(ProgressService progress) {
		this.progress = progress;
	}

	@GetMapping("/progress")
	ShowProgress show(@AuthenticationPrincipal WatchmarkUserDetails principal, @PathVariable String showRef) {
		return progress.show(principal.id(), parse(showRef));
	}

	@GetMapping("/progress/seasons/{seasonNumber}")
	SeasonProgress season(@AuthenticationPrincipal WatchmarkUserDetails principal, @PathVariable String showRef,
			@PathVariable int seasonNumber) {
		return progress.season(principal.id(), parse(showRef), seasonNumber);
	}

	@PostMapping("/rewatch")
	LibraryEntry rewatch(@AuthenticationPrincipal WatchmarkUserDetails principal, @PathVariable String showRef) {
		return progress.rewatch(principal.id(), parse(showRef));
	}

	private static ShowRef parse(String showRef) {
		return ShowRef.parse(showRef).orElseThrow(MetadataNotFoundException::show);
	}

}
