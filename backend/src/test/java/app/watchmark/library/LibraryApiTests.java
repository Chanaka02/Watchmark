package app.watchmark.library;

import static app.watchmark.auth.AuthTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import app.watchmark.TestcontainersConfiguration;
import app.watchmark.auth.AuthTestClient;

/**
 * Library over HTTP with the mock provider: rules R1 (isolation), R10 (removal
 * keeps data) and R11 (explicit permanent deletion).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class LibraryApiTests {

	private static final String SHOW = "mock:1001";

	private static final String SHOW_NAME = "Northern Lights Station";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	User alice;

	User bob;

	/** A signed-in test user: their session and CSRF/IP helper. */
	record User(AuthTestClient client, Cookie session, String email) {
	}

	@BeforeEach
	void signIn() {
		alice = newUser();
		bob = newUser();
	}

	// --- Adding and updating -------------------------------------------------------------------

	@Test
	void addingAShowStoresItsMetadataAndCreatesTheEntry() {
		assertThat(setStatus(alice, SHOW, "PLAN_TO_WATCH")).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"status": "PLAN_TO_WATCH", "rating": null, "notes": null, "cycle": 1,
					 "show": {"ref": "mock:1001", "name": "Northern Lights Station", "status": "RETURNING"}}
					""");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM episodes e JOIN shows s ON s.id = e.show_id "
				+ "WHERE s.provider = 'mock' AND s.external_id = '1001'", Integer.class))
			.as("metadata stored on first add")
			.isPositive();

		assertThat(setStatus(alice, SHOW, "WATCHING")).hasStatus(HttpStatus.OK)
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("WATCHING");
		assertThat(entryCount(alice)).isEqualTo(1);
	}

	@Test
	void unknownOrMalformedShowsCannotBeAdded() {
		assertThat(setStatus(alice, "mock:9999", "WATCHING")).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/show-not-found");
		assertThat(setStatus(alice, "tmdb:1001", "WATCHING")).as("another provider's ref")
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(setStatus(alice, "garbage", "WATCHING")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void statusIsValidated() {
		assertThat(setStatus(alice, SHOW, "BINGING")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(put(alice, "/api/v1/library/" + SHOW, "{}")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors[0].field")
			.isEqualTo("status");
	}

	@Test
	void ratingCanBeSetAndClearedWithinOneToTen() {
		setStatus(alice, SHOW, "WATCHING");

		assertThat(put(alice, "/api/v1/library/" + SHOW + "/rating", "{\"rating\": 9}")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.rating")
			.isEqualTo(9);
		assertThat(put(alice, "/api/v1/library/" + SHOW + "/rating", "{\"rating\": 0}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(put(alice, "/api/v1/library/" + SHOW + "/rating", "{\"rating\": 11}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(put(alice, "/api/v1/library/" + SHOW + "/rating", "{\"rating\": null}")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.rating")
			.isNull();
	}

	@Test
	void notesAreTrimmedBlankClearsAndLengthIsLimited() {
		setStatus(alice, SHOW, "WATCHING");

		assertThat(put(alice, "/api/v1/library/" + SHOW + "/notes", "{\"notes\": \"  Rewatch S1 finale  \"}"))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.notes")
			.isEqualTo("Rewatch S1 finale");
		assertThat(put(alice, "/api/v1/library/" + SHOW + "/notes", "{\"notes\": \"   \"}")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.notes")
			.isNull();
		assertThat(put(alice, "/api/v1/library/" + SHOW + "/notes", "{\"notes\": \"" + "x".repeat(2001) + "\"}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void ratingAndNotesRequireTheShowInTheLibrary() {
		assertThat(put(alice, "/api/v1/library/" + SHOW + "/rating", "{\"rating\": 5}"))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/not-in-library");
	}

	// --- Listing ---------------------------------------------------------------------------------

	@Test
	void libraryListsActiveEntriesWithCountsPerStatus() {
		setStatus(alice, "mock:1001", "WATCHING");
		setStatus(alice, "mock:1002", "COMPLETED");
		setStatus(alice, "mock:1005", "WATCHING");

		MvcTestResult all = get(alice, "/api/v1/library?sort=TITLE");
		assertThat(all).hasStatusOk()
			.bodyJson()
			.extractingPath("$.entries[*].show.name")
			.asArray()
			.containsExactly("Midnight Recipe Club", "Northern Lights Station", "The Quiet Archive");
		assertThat(all).bodyJson()
			.extractingPath("$.counts")
			.isEqualTo(Map.of("PLAN_TO_WATCH", 0, "WATCHING", 2, "COMPLETED", 1, "ON_HOLD", 0, "DROPPED", 0));

		assertThat(get(alice, "/api/v1/library?status=COMPLETED")).bodyJson()
			.extractingPath("$.entries[*].show.ref")
			.asArray()
			.containsExactly("mock:1002");
	}

	@Test
	void emptyLibraryIsAnEmptyListNotAnError() {
		assertThat(get(alice, "/api/v1/library")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.entries")
			.asArray()
			.isEmpty();
	}

	// --- R1: isolation ---------------------------------------------------------------------------

	@Test
	void usersNeverSeeOrChangeEachOthersEntries() {
		setStatus(alice, SHOW, "WATCHING");
		put(alice, "/api/v1/library/" + SHOW + "/notes", "{\"notes\": \"private\"}");

		assertThat(get(bob, "/api/v1/library/" + SHOW)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(bob, "/api/v1/library")).bodyJson().extractingPath("$.entries").asArray().isEmpty();
		assertThat(put(bob, "/api/v1/library/" + SHOW + "/rating", "{\"rating\": 1}"))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(delete(bob, SHOW)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(purge(bob, SHOW, SHOW_NAME)).hasStatus(HttpStatus.NOT_FOUND);

		// Bob adding the same show creates his own entry; Alice's is untouched.
		assertThat(setStatus(bob, SHOW, "DROPPED")).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.notes")
			.isNull();
		assertThat(get(alice, "/api/v1/library/" + SHOW)).bodyJson()
			.isLenientlyEqualTo("{\"status\": \"WATCHING\", \"notes\": \"private\"}");
	}

	@Test
	void libraryRequiresSignInAndCsrf() {
		assertThat(mvc.get().uri("/api/v1/library")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.put()
			.uri("/api/v1/library/" + SHOW)
			.cookie(alice.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\": \"WATCHING\"}")).as("no CSRF token").hasStatus(HttpStatus.FORBIDDEN);
	}

	// --- R10: removal keeps everything -------------------------------------------------------

	@Test
	void removingKeepsTheEntryDataAndReAddingRestoresIt() {
		setStatus(alice, SHOW, "WATCHING");
		put(alice, "/api/v1/library/" + SHOW + "/rating", "{\"rating\": 8}");
		put(alice, "/api/v1/library/" + SHOW + "/notes", "{\"notes\": \"Season 2 is great\"}");
		String addedAt = jdbc.queryForObject(
				"SELECT CAST(added_at AS CHAR) FROM user_shows WHERE user_id = ?", String.class, userId(alice));

		assertThat(delete(alice, SHOW)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(get(alice, "/api/v1/library/" + SHOW)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(alice, "/api/v1/library")).bodyJson().extractingPath("$.entries").asArray().isEmpty();
		assertThat(get(alice, "/api/v1/library")).bodyJson().extractingPath("$.counts.WATCHING").isEqualTo(0);
		assertThat(entryCount(alice)).as("row kept").isEqualTo(1);
		assertThat(delete(alice, SHOW)).as("already removed").hasStatus(HttpStatus.NOT_FOUND);

		assertThat(setStatus(alice, SHOW, "ON_HOLD")).hasStatus(HttpStatus.OK)
			.bodyJson()
			.isLenientlyEqualTo(
					"{\"status\": \"ON_HOLD\", \"rating\": 8, \"notes\": \"Season 2 is great\", \"cycle\": 1}");
		assertThat(jdbc.queryForObject("SELECT CAST(added_at AS CHAR) FROM user_shows WHERE user_id = ?",
				String.class, userId(alice)))
			.as("original added date kept")
			.isEqualTo(addedAt);
	}

	// --- R11: explicit permanent deletion ------------------------------------------------------

	@Test
	void purgeRequiresTheExactShowName() {
		setStatus(alice, SHOW, "WATCHING");

		assertThat(purge(alice, SHOW, "Northern Lights")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/confirmation-mismatch");
		assertThat(entryCount(alice)).as("nothing deleted").isEqualTo(1);
		assertThat(purge(alice, SHOW, "")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void purgeDeletesOnlyThisUsersDataForThisShow() {
		setStatus(alice, SHOW, "WATCHING");
		setStatus(alice, "mock:1002", "COMPLETED");
		setStatus(bob, SHOW, "WATCHING");

		assertThat(purge(alice, SHOW, "  northern   lights STATION ")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(entryCount(alice)).isEqualTo(1);
		assertThat(get(alice, "/api/v1/library/mock:1002")).hasStatusOk();
		assertThat(get(bob, "/api/v1/library/" + SHOW)).hasStatusOk();
	}

	@Test
	void purgeWorksOnARemovedEntryAndReAddingStartsFresh() {
		setStatus(alice, SHOW, "WATCHING");
		put(alice, "/api/v1/library/" + SHOW + "/notes", "{\"notes\": \"old\"}");
		delete(alice, SHOW);

		assertThat(purge(alice, SHOW, SHOW_NAME)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(entryCount(alice)).isZero();
		assertThat(purge(alice, SHOW, SHOW_NAME)).hasStatus(HttpStatus.NOT_FOUND);

		assertThat(setStatus(alice, SHOW, "PLAN_TO_WATCH")).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.notes")
			.isNull();
	}

	// --- Concurrency -----------------------------------------------------------------------------

	@Test
	void concurrentAddsOfTheSameShowCreateOneEntry() throws Exception {
		setStatus(alice, "mock:1002", "WATCHING"); // fetch CSRF token and store metadata up front
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			List<Future<MvcTestResult>> results = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				results.add(pool.submit(() -> {
					start.await();
					return setStatus(alice, "mock:1006", "WATCHING");
				}));
			}
			start.countDown();
			for (Future<MvcTestResult> result : results) {
				assertThat(result.get().getResponse().getStatus()).isIn(200, 201);
			}
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(entryCount(alice)).isEqualTo(2);
	}

	// --- Helpers ---------------------------------------------------------------------------------

	private User newUser() {
		AuthTestClient client = new AuthTestClient(mvc);
		String email = uniqueEmail();
		return new User(client, client.registerAndGetSession(email), email);
	}

	private long userId(User user) {
		return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, user.email());
	}

	private int entryCount(User user) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM user_shows WHERE user_id = ?", Integer.class, userId(user));
	}

	private MvcTestResult setStatus(User user, String ref, String status) {
		return put(user, "/api/v1/library/" + ref, "{\"status\": \"" + status + "\"}");
	}

	private MvcTestResult get(User user, String uri) {
		return mvc.get().uri(uri).cookie(user.session()).with(user.client().fromClient()).exchange();
	}

	private MvcTestResult put(User user, String uri, String json) {
		return mvc.put()
			.uri(uri)
			.cookie(user.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.with(user.client().csrf())
			.with(user.client().fromClient())
			.exchange();
	}

	private MvcTestResult delete(User user, String ref) {
		return mvc.delete()
			.uri("/api/v1/library/" + ref)
			.cookie(user.session())
			.with(user.client().csrf())
			.with(user.client().fromClient())
			.exchange();
	}

	private MvcTestResult purge(User user, String ref, String confirmName) {
		return mvc.post()
			.uri("/api/v1/library/" + ref + "/purge")
			.cookie(user.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirmName\": \"" + confirmName + "\"}")
			.with(user.client().csrf())
			.with(user.client().fromClient())
			.exchange();
	}

}
