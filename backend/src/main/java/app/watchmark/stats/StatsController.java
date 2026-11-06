package app.watchmark.stats;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import app.watchmark.auth.WatchmarkUserDetails;
import app.watchmark.stats.StatsDtos.Stats;

/** The signed-in user's statistics (R1: the user always comes from the session). */
@RestController
@RequestMapping("/api/v1/me/stats")
class StatsController {

	private final StatsService stats;

	StatsController(StatsService stats) {
		this.stats = stats;
	}

	/** @param range {@code week}, {@code month} (default) or {@code all} */
	@GetMapping
	Stats get(@AuthenticationPrincipal WatchmarkUserDetails principal, @RequestParam(required = false) String range) {
		return stats.get(principal.id(), range);
	}

}
