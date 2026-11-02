package app.watchmark.dashboard;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.watchmark.auth.WatchmarkUserDetails;
import app.watchmark.dashboard.DashboardDtos.Dashboard;

/** The signed-in user's dashboard (R1: the user always comes from the session). */
@RestController
@RequestMapping("/api/v1/me/dashboard")
class DashboardController {

	private final DashboardService dashboard;

	DashboardController(DashboardService dashboard) {
		this.dashboard = dashboard;
	}

	@GetMapping
	Dashboard get(@AuthenticationPrincipal WatchmarkUserDetails principal) {
		return dashboard.get(principal.id());
	}

}
