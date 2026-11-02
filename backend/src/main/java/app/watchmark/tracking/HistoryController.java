package app.watchmark.tracking;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import app.watchmark.auth.WatchmarkUserDetails;
import app.watchmark.tracking.dto.TrackingDtos.HistoryPage;

/** The signed-in user's viewing history (R1: the user always comes from the session). */
@RestController
@RequestMapping("/api/v1/me/history")
class HistoryController {

	private final HistoryService history;

	HistoryController(HistoryService history) {
		this.history = history;
	}

	@GetMapping
	HistoryPage page(@AuthenticationPrincipal WatchmarkUserDetails principal,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
		return history.page(principal.id(), cursor, size);
	}

}
