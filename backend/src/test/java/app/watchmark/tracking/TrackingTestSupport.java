package app.watchmark.tracking;

import static app.watchmark.auth.AuthTestClient.uniqueEmail;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.Cookie;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import app.watchmark.MutableClock;
import app.watchmark.TestcontainersConfiguration;
import app.watchmark.auth.AuthTestClient;
import app.watchmark.catalog.ShowRef;
import app.watchmark.catalog.ShowStatus;
import app.watchmark.catalog.dto.CatalogDtos.EpisodeInfo;
import app.watchmark.catalog.dto.CatalogDtos.SeasonDetails;
import app.watchmark.catalog.dto.CatalogDtos.SeasonSummary;
import app.watchmark.catalog.dto.CatalogDtos.ShowDetails;
import app.watchmark.catalog.store.FakeMetadataProvider;
import app.watchmark.catalog.store.FakeProviderConfig;
import app.watchmark.catalog.store.MetadataSyncService;

/**
 * Shared setup for tracking tests over HTTP: scripted provider data, a clock the
 * tests move, signed-in users and request helpers. Today is 2026-11-02 in UTC
 * and in the test users' zone (Asia/Colombo) at {@link #NOW}.
 */
@Import({ TestcontainersConfiguration.class, FakeProviderConfig.class })
@SpringBootTest
@AutoConfigureMockMvc
abstract class TrackingTestSupport {

	static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

	static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

	private static final AtomicInteger showIds = new AtomicInteger(7000);

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	FakeMetadataProvider provider;

	@Autowired
	MutableClock clock;

	@Autowired
	MetadataSyncService sync;

	/** A signed-in test user. */
	record User(AuthTestClient client, Cookie session, String email, long id) {
	}

	/** One episode of a scripted show. */
	record Ep(int season, int number, LocalDate airDate) {
	}

	static Ep ep(int season, int number, LocalDate airDate) {
		return new Ep(season, number, airDate);
	}

	@BeforeEach
	void resetProviderAndClock() {
		provider.reset();
		clock.set(NOW);
	}

	// --- Scripted shows ----------------------------------------------------------------------------

	/** Publishes a show at the fake provider and returns its ref ({@code tmdb:<id>}). */
	String publish(ShowStatus status, Ep... episodes) {
		String id = Integer.toString(showIds.incrementAndGet());
		publishAs(id, status, List.of(episodes));
		return "tmdb:" + id;
	}

	/** (Re)publishes the show's data; a later sync applies it. */
	void publishAs(String id, ShowStatus status, List<Ep> episodes) {
		Map<Integer, List<EpisodeInfo>> bySeason = new LinkedHashMap<>();
		for (Ep e : episodes) {
			bySeason.computeIfAbsent(e.season(), n -> new ArrayList<>())
				.add(new EpisodeInfo(id + "-" + e.season() + "-" + e.number(), e.season(), e.number(),
						"Episode " + e.number(), null, e.airDate(), 30, null));
		}
		List<SeasonSummary> summaries = new ArrayList<>();
		provider.seasons.keySet().removeIf(key -> key.startsWith(id + "/"));
		bySeason.forEach((number, list) -> {
			summaries.add(new SeasonSummary(number, number == 0 ? "Specials" : "Season " + number, list.size(),
					list.get(0).airDate(), null));
			provider.seasons.put(id + "/" + number,
					new SeasonDetails("tmdb:" + id, number, "Season " + number, null, null, null, list));
		});
		provider.shows.put(id, new ShowDetails("tmdb:" + id, "Show " + id, null, null, null, status, null, null, null,
				null, List.of(), List.of(), null, summaries.size(), episodes.size(), null, summaries, null, null,
				List.of()));
	}

	/** Stores the show's metadata without adding it to anyone's library. */
	void store(String showRef) {
		sync.sync(ShowRef.parse(showRef).orElseThrow());
	}

	long episodeId(String showRef, int season, int number) {
		return jdbc.queryForObject("""
				SELECT e.id FROM episodes e JOIN shows s ON s.id = e.show_id
				WHERE s.provider = 'tmdb' AND s.external_id = ? AND e.season_number = ? AND e.episode_number = ?
				""", Long.class, showRef.substring("tmdb:".length()), season, number);
	}

	// --- Users -------------------------------------------------------------------------------------

	User newUser() {
		AuthTestClient client = new AuthTestClient(mvc);
		String email = uniqueEmail();
		Cookie session = client.registerAndGetSession(email);
		long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
		return new User(client, session, email, id);
	}

	void setTimeZone(User user, String zone) {
		jdbc.update("UPDATE users SET time_zone = ? WHERE id = ?", zone, user.id());
	}

	// --- Requests ----------------------------------------------------------------------------------

	MvcTestResult addToLibrary(User user, String showRef, String status) {
		return send(user, mvc.put().uri("/api/v1/library/" + showRef), "{\"status\": \"" + status + "\"}");
	}

	MvcTestResult watch(User user, long episodeId) {
		return send(user, mvc.put().uri("/api/v1/tracking/episodes/" + episodeId + "/watch"), null);
	}

	MvcTestResult unwatch(User user, long episodeId) {
		return send(user, mvc.delete().uri("/api/v1/tracking/episodes/" + episodeId + "/watch"), null);
	}

	MvcTestResult watchUpTo(User user, long episodeId) {
		return send(user, mvc.post().uri("/api/v1/tracking/episodes/" + episodeId + "/watch-up-to"), null);
	}

	MvcTestResult watchSeason(User user, String showRef, int season) {
		return send(user, mvc.post().uri("/api/v1/tracking/shows/" + showRef + "/seasons/" + season + "/watch"), null);
	}

	MvcTestResult undo(User user, String operationId) {
		return send(user, mvc.post().uri("/api/v1/tracking/operations/" + operationId + "/undo"), null);
	}

	MvcTestResult rewatch(User user, String showRef) {
		return send(user, mvc.post().uri("/api/v1/library/" + showRef + "/rewatch"), null);
	}

	MvcTestResult progress(User user, String showRef) {
		return mvc.get()
			.uri("/api/v1/library/" + showRef + "/progress")
			.cookie(user.session())
			.with(user.client().fromClient())
			.exchange();
	}

	MvcTestResult seasonProgress(User user, String showRef, int season) {
		return mvc.get()
			.uri("/api/v1/library/" + showRef + "/progress/seasons/" + season)
			.cookie(user.session())
			.with(user.client().fromClient())
			.exchange();
	}

	MvcTestResult entry(User user, String showRef) {
		return mvc.get()
			.uri("/api/v1/library/" + showRef)
			.cookie(user.session())
			.with(user.client().fromClient())
			.exchange();
	}

	private MvcTestResult send(User user, MockMvcTester.MockMvcRequestBuilder request, String json) {
		var builder = request.cookie(user.session()).with(user.client().csrf()).with(user.client().fromClient());
		if (json != null) {
			builder = builder.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return builder.exchange();
	}

	/** Reads one value from a JSON response. */
	static <T> T read(MvcTestResult result, String path) {
		try {
			return JsonPath.read(result.getResponse().getContentAsString(), path);
		}
		catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** The operation id returned by a tracking request (asserting it was recorded). */
	static String operationId(MvcTestResult result) {
		String id = read(result, "$.operationId");
		if (id == null) {
			throw new AssertionError("no operation recorded: " + result.getResponse().getStatus());
		}
		return id;
	}

	// --- Database checks ---------------------------------------------------------------------------

	int activeWatches(User user) {
		return count("SELECT COUNT(*) FROM episode_watches WHERE user_id = ? AND deleted_at IS NULL", user.id());
	}

	int allWatchRows(User user) {
		return count("SELECT COUNT(*) FROM episode_watches WHERE user_id = ?", user.id());
	}

	int operations(User user) {
		return count("SELECT COUNT(*) FROM tracking_operations WHERE user_id = ?", user.id());
	}

	String libraryStatus(User user, String showRef) {
		return jdbc.queryForObject("""
				SELECT us.status FROM user_shows us JOIN shows s ON s.id = us.show_id
				WHERE us.user_id = ? AND s.provider = 'tmdb' AND s.external_id = ?
				""", String.class, user.id(), showRef.substring("tmdb:".length()));
	}

	int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

}
