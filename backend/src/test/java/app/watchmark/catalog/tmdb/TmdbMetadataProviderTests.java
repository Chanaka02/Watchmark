package app.watchmark.catalog.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import app.watchmark.catalog.DiscoverList;
import app.watchmark.catalog.MetadataNotFoundException;
import app.watchmark.catalog.MetadataUnavailableException;
import app.watchmark.catalog.ShowStatus;
import app.watchmark.catalog.dto.CatalogDtos.CastMember;
import app.watchmark.catalog.dto.CatalogDtos.EpisodeInfo;
import app.watchmark.catalog.dto.CatalogDtos.SeasonSummary;

class TmdbMetadataProviderTests {

	private static final String API = "https://api.themoviedb.org/3";

	private final TmdbProperties properties = new TmdbProperties("test-token", URI.create(API),
			URI.create("https://image.tmdb.org/t/p"), "en-US", Duration.ofSeconds(3), Duration.ofSeconds(5), 1000,
			"w342", "w1280", "w185", "w300");

	private MockRestServiceServer server;

	private TmdbMetadataProvider provider;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
			.baseUrl(API)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
		server = MockRestServiceServer.bindTo(builder).build();
		provider = new TmdbMetadataProvider(builder.build(), properties, new RequestPacer(1000));
	}

	@AfterEach
	void verify() {
		server.verify();
	}

	// --- Lists and search -------------------------------------------------------

	@Test
	void popularPageIsMappedDefensively() throws IOException {
		server.expect(requestTo(API + "/tv/popular?page=2&language=en-US"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
			.andRespond(withSuccess(fixture("tv-popular-page-2.json"), MediaType.APPLICATION_JSON));

		var page = provider.discover(DiscoverList.POPULAR, 2);

		assertThat(page.page()).isEqualTo(2);
		assertThat(page.totalResults()).isEqualTo(182390);
		assertThat(page.totalPages()).as("TMDB serves at most 500 pages").isEqualTo(500);
		// Adult entry and entry without an id are dropped.
		assertThat(page.items()).extracting("ref").containsExactly("tmdb:90001", "tmdb:90002");

		var first = page.items().get(0);
		assertThat(first.name()).isEqualTo("Fixture Harbour");
		assertThat(first.overview()).isEqualTo("An invented drama used only in tests.");
		assertThat(first.firstAirDate()).isEqualTo(LocalDate.of(2021, 3, 14));
		assertThat(first.posterUrl()).isEqualTo("https://image.tmdb.org/t/p/w342/fixturePoster1.jpg");

		var sparse = page.items().get(1);
		assertThat(sparse.name()).as("falls back to original name").isEqualTo("Tesuto Bangumi");
		assertThat(sparse.overview()).isNull();
		assertThat(sparse.firstAirDate()).as("empty date string").isNull();
		assertThat(sparse.posterUrl()).isNull();
	}

	@ParameterizedTest
	@CsvSource({ "TRENDING, /trending/tv/week", "POPULAR, /tv/popular", "TOP_RATED, /tv/top_rated",
			"ON_THE_AIR, /tv/on_the_air" })
	void eachDiscoverListCallsItsEndpoint(DiscoverList list, String path) {
		server.expect(requestTo(API + path + "?page=1&language=en-US"))
			.andRespond(withSuccess("{\"page\":1,\"results\":[],\"total_pages\":0,\"total_results\":0}",
					MediaType.APPLICATION_JSON));

		assertThat(provider.discover(list, 1).items()).isEmpty();
	}

	@Test
	void searchEncodesTheQueryAndExcludesAdultTitles() {
		server.expect(requestTo(API + "/search/tv?query=dr%20who%20%26%20co&include_adult=false&page=1&language=en-US"))
			.andRespond(withSuccess("{\"page\":1,\"results\":[],\"total_pages\":0,\"total_results\":0}",
					MediaType.APPLICATION_JSON));

		assertThat(provider.search("dr who & co", 1).totalResults()).isZero();
	}

	@Test
	void missingResultsArrayIsAnEmptyPage() {
		server.expect(requestTo(API + "/tv/popular?page=1&language=en-US"))
			.andRespond(withSuccess("{\"page\":1}", MediaType.APPLICATION_JSON));

		var page = provider.discover(DiscoverList.POPULAR, 1);
		assertThat(page.items()).isEmpty();
		assertThat(page.totalPages()).isZero();
	}

	// --- Show and season details --------------------------------------------------

	@Test
	void showDetailsAreMappedDefensively() throws IOException {
		server.expect(requestTo(API + "/tv/90001?append_to_response=credits&language=en-US"))
			.andRespond(withSuccess(fixture("tv-details-90001.json"), MediaType.APPLICATION_JSON));

		var show = provider.show("90001");

		assertThat(show.ref()).isEqualTo("tmdb:90001");
		assertThat(show.status()).isEqualTo(ShowStatus.RETURNING);
		assertThat(show.tagline()).as("empty string").isNull();
		assertThat(show.genres()).as("blank genre names dropped").containsExactly("Drama", "Mystery");
		assertThat(show.networks()).containsExactly("Fixture Broadcasting");
		assertThat(show.episodeRunTimeMinutes()).as("empty episode_run_time").isNull();
		assertThat(show.backdropUrl()).isEqualTo("https://image.tmdb.org/t/p/w1280/fixtureBackdrop1.jpg");
		assertThat(show.nextEpisode()).isNull();
		assertThat(show.lastEpisode()).extracting(EpisodeInfo::seasonNumber, EpisodeInfo::episodeNumber,
				EpisodeInfo::airDate, EpisodeInfo::runtimeMinutes)
			.containsExactly(2, 3, LocalDate.of(2026, 10, 20), 52);

		assertThat(show.seasons()).extracting(SeasonSummary::seasonNumber).containsExactly(0, 1, 2, 3);
		assertThat(show.seasons().get(0).airDate()).isNull();
		assertThat(show.seasons().get(3).name()).as("fallback label").isEqualTo("Season 3");
		assertThat(show.seasons().get(1).posterUrl()).isEqualTo("https://image.tmdb.org/t/p/w342/season1.jpg");

		assertThat(show.cast()).extracting(CastMember::name)
			.as("billing order; nameless entries dropped")
			.containsExactly("Lead Performer", "Second Performer", "Third Billed");
		assertThat(show.cast().get(0).profileUrl()).isEqualTo("https://image.tmdb.org/t/p/w185/lead.jpg");
		assertThat(show.cast().get(2).character()).isNull();
	}

	@Test
	void seasonEpisodesAreSortedAndIncompleteOnesHandled() throws IOException {
		server.expect(requestTo(API + "/tv/90001/season/1?language=en-US"))
			.andRespond(withSuccess(fixture("tv-90001-season-1.json"), MediaType.APPLICATION_JSON));

		var season = provider.season("90001", 1);

		assertThat(season.showRef()).isEqualTo("tmdb:90001");
		assertThat(season.name()).isEqualTo("Series 1");
		assertThat(season.overview()).isNull();
		// The entry without an episode number cannot be addressed and is dropped.
		assertThat(season.episodes()).extracting(EpisodeInfo::episodeNumber).containsExactly(1, 2, 3);

		var first = season.episodes().get(0);
		assertThat(first.externalId()).isEqualTo("700101");
		assertThat(first.runtimeMinutes()).isEqualTo(58);
		assertThat(first.stillUrl()).isEqualTo("https://image.tmdb.org/t/p/w300/still1.jpg");

		assertThat(season.episodes().get(1).runtimeMinutes()).as("runtime 0 means unknown").isNull();

		var third = season.episodes().get(2);
		assertThat(third.airDate()).as("unknown date is never guessed").isNull();
		assertThat(third.name()).isEqualTo("Episode 3");
		assertThat(third.runtimeMinutes()).isNull();
	}

	@Test
	void adultShowIsTreatedAsNotFound() {
		server.expect(requestTo(API + "/tv/5?append_to_response=credits&language=en-US"))
			.andRespond(withSuccess("{\"id\":5,\"name\":\"x\",\"adult\":true}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> provider.show("5")).isInstanceOf(MetadataNotFoundException.class);
	}

	// --- Failures -------------------------------------------------------------------

	@Test
	void unknownShowIsNotFound() {
		server.expect(requestTo(API + "/tv/404?append_to_response=credits&language=en-US"))
			.andRespond(withResourceNotFound());

		assertThatThrownBy(() -> provider.show("404")).isInstanceOfSatisfying(MetadataNotFoundException.class,
				ex -> assertThat(ex.type()).isEqualTo("show-not-found"));
	}

	@Test
	void unknownSeasonIsNotFound() {
		server.expect(requestTo(API + "/tv/90001/season/42?language=en-US")).andRespond(withResourceNotFound());

		assertThatThrownBy(() -> provider.season("90001", 42))
			.isInstanceOfSatisfying(MetadataNotFoundException.class,
					ex -> assertThat(ex.type()).isEqualTo("season-not-found"));
	}

	@Test
	void rateLimitIsUnavailableWithRetryAfter() {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.RETRY_AFTER, "7");
		server.expect(requestTo(API + "/tv/popular?page=1&language=en-US"))
			.andRespond(withTooManyRequests().headers(headers));

		assertThatThrownBy(() -> provider.discover(DiscoverList.POPULAR, 1))
			.isInstanceOfSatisfying(MetadataUnavailableException.class, ex -> {
				assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
				assertThat(ex.retryAfter()).isEqualTo(Duration.ofSeconds(7));
			});
	}

	@Test
	void serverErrorIsUnavailable() {
		server.expect(requestTo(API + "/tv/popular?page=1&language=en-US")).andRespond(withServerError());

		assertThatThrownBy(() -> provider.discover(DiscoverList.POPULAR, 1))
			.isInstanceOf(MetadataUnavailableException.class);
	}

	@Test
	void rejectedTokenIsUnavailableNotUnauthorizedForTheUser() {
		server.expect(requestTo(API + "/tv/popular?page=1&language=en-US")).andRespond(withUnauthorizedRequest());

		assertThatThrownBy(() -> provider.discover(DiscoverList.POPULAR, 1))
			.isInstanceOfSatisfying(MetadataUnavailableException.class,
					ex -> assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
	}

	@Test
	void notFoundOnAListIsUnavailableRatherThanShowNotFound() {
		server.expect(requestTo(API + "/tv/popular?page=1&language=en-US")).andRespond(withResourceNotFound());

		assertThatThrownBy(() -> provider.discover(DiscoverList.POPULAR, 1))
			.isInstanceOf(MetadataUnavailableException.class);
	}

	@Test
	void timeoutIsUnavailable() {
		server.expect(requestTo(API + "/tv/popular?page=1&language=en-US"))
			.andRespond(withException(new SocketTimeoutException("read timed out")));

		assertThatThrownBy(() -> provider.discover(DiscoverList.POPULAR, 1))
			.isInstanceOf(MetadataUnavailableException.class);
	}

	@Test
	void malformedJsonIsUnavailable() {
		server.expect(requestTo(API + "/tv/90001?append_to_response=credits&language=en-US"))
			.andRespond(withSuccess("{\"id\": 90001, \"name\": ", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> provider.show("90001")).isInstanceOf(MetadataUnavailableException.class);
	}

	@Test
	void emptyBodyIsUnavailable() {
		server.expect(requestTo(API + "/tv/90001?append_to_response=credits&language=en-US"))
			.andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> provider.show("90001")).isInstanceOf(MetadataUnavailableException.class);
	}

	// --- Mapping helpers -------------------------------------------------------------

	@ParameterizedTest
	@CsvSource({ "Returning Series, RETURNING", "Ended, ENDED", "Canceled, CANCELED", "Cancelled, CANCELED",
			"In Production, IN_PRODUCTION", "Planned, PLANNED", "Pilot, PILOT", "Something New, UNKNOWN" })
	void statusIsNormalised(String tmdbStatus, ShowStatus expected) {
		assertThat(TmdbMapper.status(tmdbStatus)).isEqualTo(expected);
	}

	@Test
	void unparsableDatesBecomeNull() {
		assertThat(TmdbMapper.date("2026-02-30")).isNull();
		assertThat(TmdbMapper.date("soon")).isNull();
		assertThat(TmdbMapper.date(" 2026-10-28 ")).isEqualTo(LocalDate.of(2026, 10, 28));
	}

	private static String fixture(String name) throws IOException {
		return new ClassPathResource("tmdb/" + name).getContentAsString(StandardCharsets.UTF_8);
	}

}
