package app.watchmark.catalog;

import static app.watchmark.auth.AuthTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import app.watchmark.TestcontainersConfiguration;
import app.watchmark.auth.AuthTestClient;
import app.watchmark.catalog.dto.CatalogDtos.ResultPage;
import app.watchmark.catalog.dto.CatalogDtos.ShowDetails;

/** Provider failures and caching, with the provider replaced by a Mockito mock. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CatalogFailureAndCachingTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	CacheManager cacheManager;

	@MockitoBean
	MetadataProvider provider;

	Cookie session;

	@BeforeEach
	void setUp() {
		cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
		given(provider.id()).willReturn("tmdb");
		session = new AuthTestClient(mvc).registerAndGetSession(uniqueEmail());
	}

	@Test
	void unavailableProviderIsReportedAs503WithRetryAfter() {
		given(provider.discover(DiscoverList.POPULAR, 1))
			.willThrow(new MetadataUnavailableException(Duration.ofSeconds(30), null));

		assertThat(mvc.get().uri("/api/v1/catalog/discover/popular").cookie(session))
			.hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
			.headers()
			.hasValue("Retry-After", "30");
		assertThat(mvc.get().uri("/api/v1/catalog/discover/popular").cookie(session)).bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/metadata-unavailable");
	}

	@Test
	void unexpectedProviderErrorIsA500WithoutDetails() {
		given(provider.show("1")).willThrow(new IllegalStateException("secret upstream detail"));

		var result = mvc.get().uri("/api/v1/catalog/shows/tmdb:1").cookie(session).exchange();
		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(result).bodyText().doesNotContain("secret upstream detail");
	}

	@Test
	void successfulResponsesAreCached() {
		given(provider.show("7")).willReturn(show("tmdb:7"));

		for (int i = 0; i < 3; i++) {
			assertThat(mvc.get().uri("/api/v1/catalog/shows/tmdb:7").cookie(session)).hasStatusOk();
		}
		verify(provider, times(1)).show("7");
	}

	@Test
	void equivalentSearchesShareACacheEntry() {
		given(provider.search(eq("fixture harbour"), anyInt())).willReturn(ResultPage.empty(1));

		mvc.get().uri("/api/v1/catalog/search").param("q", "Fixture Harbour").cookie(session).exchange();
		mvc.get().uri("/api/v1/catalog/search").param("q", " fixture  HARBOUR").cookie(session).exchange();

		verify(provider, times(1)).search(eq("fixture harbour"), anyInt());
	}

	@Test
	void failuresAreNotCached() {
		given(provider.show(anyString())).willThrow(new MetadataUnavailableException(null, null))
			.willReturn(show("tmdb:8"));

		assertThat(mvc.get().uri("/api/v1/catalog/shows/tmdb:8").cookie(session))
			.hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(mvc.get().uri("/api/v1/catalog/shows/tmdb:8").cookie(session)).hasStatusOk();
		verify(provider, times(2)).show("8");
	}

	private static ShowDetails show(String ref) {
		return new ShowDetails(ref, "Fixture", null, null, null, ShowStatus.UNKNOWN, null, null, null, null, List.of(),
				List.of(), null, null, null, null, List.of(), null, null, List.of());
	}

}
