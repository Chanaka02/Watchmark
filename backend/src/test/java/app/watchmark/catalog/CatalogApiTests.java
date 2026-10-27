package app.watchmark.catalog;

import static app.watchmark.auth.AuthTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import app.watchmark.TestcontainersConfiguration;
import app.watchmark.auth.AuthTestClient;

/** Catalog endpoints over HTTP, backed by the mock provider (forced in the Surefire config). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CatalogApiTests {

	@Autowired
	MockMvcTester mvc;

	Cookie session;

	@BeforeEach
	void signIn() {
		session = new AuthTestClient(mvc).registerAndGetSession(uniqueEmail());
	}

	@Test
	void sourceIsPublicAndReportsMockData() {
		assertThat(mvc.get().uri("/api/v1/catalog/source")).hasStatusOk()
			.bodyJson()
			.isStrictlyEqualTo("{\"provider\": \"mock\", \"mock\": true}");
	}

	@Test
	void catalogRequiresSignIn() {
		assertThat(mvc.get().uri("/api/v1/catalog/discover/trending")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/api/v1/catalog/search?q=lights")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/api/v1/catalog/shows/mock:1001")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// --- Discover ---------------------------------------------------------------------

	@Test
	void discoverReturnsAPage() {
		assertThat(get("/api/v1/catalog/discover/trending?page=2")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"page\": 2, \"totalPages\": 2, \"totalResults\": 8}");
	}

	@Test
	void missingValuesAreNullNotInvented() {
		var result = get("/api/v1/catalog/discover/trending");
		assertThat(result).hasStatusOk().bodyJson().extractingPath("$.items[0].ref").isEqualTo("mock:1005");
		assertThat(result).bodyJson().extractingPath("$.items[0].posterUrl").isNull();
		// "Harbor Lights" has no overview; "Paper Satellites" has no air date or rating.
		assertThat(search("harbor")).bodyJson().extractingPath("$.items[0].overview").isNull();
		var planned = search("paper satellites");
		assertThat(planned).bodyJson().extractingPath("$.items[0].firstAirDate").isNull();
		assertThat(planned).bodyJson().extractingPath("$.items[0].voteAverage").isNull();
	}

	@Test
	void unknownListIsNotFound() {
		assertThat(get("/api/v1/catalog/discover/best-ever")).hasStatus(HttpStatus.NOT_FOUND)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void pageIsValidated() {
		assertThat(get("/api/v1/catalog/discover/trending?page=0")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors[0].field")
			.isEqualTo("page");
		assertThat(get("/api/v1/catalog/discover/trending?page=501")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(get("/api/v1/catalog/discover/trending?page=abc")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/bad-request");
	}

	// --- Search -----------------------------------------------------------------------

	@Test
	void searchFindsShowsIgnoringCaseAndSpacing() {
		assertThat(search("  NORTHERN  lights ")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.items[*].ref")
			.asArray()
			.containsExactly("mock:1001");
	}

	@Test
	void searchWithNoMatchesIsAnEmptyPage() {
		assertThat(search("zzzz")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"items\": [], \"totalResults\": 0}");
	}

	@Test
	void searchQueryIsValidated() {
		assertThat(search("   ")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(get("/api/v1/catalog/search")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(search("x".repeat(101))).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void searchIsRateLimitedPerUser() {
		for (int i = 0; i < 60; i++) {
			assertThat(search("lights")).hasStatusOk();
		}
		assertThat(search("lights")).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
			.headers()
			.containsHeader("Retry-After");
	}

	// --- Show and season ---------------------------------------------------------------

	@Test
	void showDetailsIncludeSeasonsAndUpcomingEpisode() {
		assertThat(get("/api/v1/catalog/shows/mock:1001")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"ref": "mock:1001", "name": "Northern Lights Station", "status": "RETURNING",
					 "nextEpisode": {"seasonNumber": 2, "episodeNumber": 5},
					 "posterUrl": null}
					""");
	}

	@Test
	void showFromAnotherProviderOrMalformedRefIsNotFound() {
		assertThat(get("/api/v1/catalog/shows/tmdb:1001")).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/show-not-found");
		assertThat(get("/api/v1/catalog/shows/not-a-ref")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get("/api/v1/catalog/shows/mock:9999")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void seasonListsEpisodesWithUnknownDatesAsNull() {
		assertThat(get("/api/v1/catalog/shows/mock:1001/seasons/0")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.episodes[1].airDate")
			.isNull();
	}

	@Test
	void unknownOrInvalidSeasonIsRejected() {
		assertThat(get("/api/v1/catalog/shows/mock:1001/seasons/9")).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/season-not-found");
		assertThat(get("/api/v1/catalog/shows/mock:1001/seasons/-1")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	private MvcTestResult get(String uri) {
		return mvc.get().uri(uri).cookie(session).exchange();
	}

	/** Uses param() so the query is sent exactly as given (uri() would re-encode '%'). */
	private MvcTestResult search(String query) {
		return mvc.get().uri("/api/v1/catalog/search").param("q", query).cookie(session).exchange();
	}

}
