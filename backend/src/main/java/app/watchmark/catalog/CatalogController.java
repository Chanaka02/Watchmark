package app.watchmark.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import app.watchmark.auth.WatchmarkUserDetails;
import app.watchmark.catalog.dto.CatalogDtos.CatalogSource;
import app.watchmark.catalog.dto.CatalogDtos.ResultPage;
import app.watchmark.catalog.dto.CatalogDtos.SeasonDetails;
import app.watchmark.catalog.dto.CatalogDtos.ShowDetails;
import app.watchmark.catalog.dto.CatalogDtos.ShowSummary;
import app.watchmark.common.ApiException;
import app.watchmark.common.RateLimitProperties;
import app.watchmark.common.RateLimiter;

/**
 * Show discovery and details. Everything except {@code /source} requires a
 * signed-in user (see SecurityConfig), which bounds use of the provider quota.
 */
@RestController
@RequestMapping("/api/v1/catalog")
class CatalogController {

	/** Highest page TMDB serves for any list. */
	static final int MAX_PAGE = 500;

	private final CatalogService catalog;

	private final RateLimiter rateLimiter;

	private final RateLimitProperties limits;

	CatalogController(CatalogService catalog, RateLimiter rateLimiter, RateLimitProperties limits) {
		this.catalog = catalog;
		this.rateLimiter = rateLimiter;
		this.limits = limits;
	}

	/** Which provider is active, so the UI can show attribution or a mock-data notice. Public. */
	@GetMapping("/source")
	CatalogSource source() {
		return catalog.source();
	}

	@GetMapping("/discover/{list}")
	ResultPage<ShowSummary> discover(@PathVariable String list,
			@RequestParam(defaultValue = "1") @Min(1) @Max(MAX_PAGE) int page) {
		DiscoverList discoverList = DiscoverList.fromSlug(list)
			.orElseThrow(() -> ApiException.notFound("Unknown list."));
		return catalog.discover(discoverList, page);
	}

	@GetMapping("/search")
	ResultPage<ShowSummary> search(@AuthenticationPrincipal WatchmarkUserDetails principal,
			@RequestParam @NotBlank @Size(max = 100) String q,
			@RequestParam(defaultValue = "1") @Min(1) @Max(MAX_PAGE) int page) {
		rateLimiter.acquire("search-user:" + principal.id(), limits.searchPerUser());
		return catalog.search(CatalogService.normalizeQuery(q), page);
	}

	@GetMapping("/shows/{showRef}")
	ShowDetails show(@PathVariable String showRef) {
		return catalog.show(parse(showRef));
	}

	@GetMapping("/shows/{showRef}/seasons/{seasonNumber}")
	SeasonDetails season(@PathVariable String showRef, @PathVariable @Min(0) @Max(1000) int seasonNumber) {
		return catalog.season(parse(showRef), seasonNumber);
	}

	private static ShowRef parse(String showRef) {
		return ShowRef.parse(showRef).orElseThrow(MetadataNotFoundException::show);
	}

}
