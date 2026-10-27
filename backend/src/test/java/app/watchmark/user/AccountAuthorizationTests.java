package app.watchmark.user;

import static app.watchmark.auth.AuthTestClient.PASSWORD;
import static app.watchmark.auth.AuthTestClient.sessionCookie;
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

/** Profile, password and account deletion, including authorization failures. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AccountAuthorizationTests {

	private static final String NEW_PASSWORD = "a brand new passphrase 2026";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	UserRepository users;

	AuthTestClient client;

	@BeforeEach
	void setUp() {
		client = new AuthTestClient(mvc);
	}

	// --- Unauthenticated access -------------------------------------------------

	@Test
	void personalEndpointsRequireAuthentication() {
		assertThat(mvc.get().uri("/api/v1/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/unauthenticated");
		assertThat(mvc.get().uri("/api/v1/me/profile")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.patch()
			.uri("/api/v1/me/profile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"displayName\": \"x\"}")
			.with(client.csrf())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.delete()
			.uri("/api/v1/me")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"password\": \"x\"}")
			.with(client.csrf())).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void forgedOrUnknownSessionCookieIsUnauthenticated() {
		assertThat(client.me(new Cookie(AuthTestClient.SESSION_COOKIE, "bm90LWEtcmVhbC1zZXNzaW9u")))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void authenticatedStateChangeStillRequiresCsrfToken() {
		Cookie session = client.registerAndGetSession(uniqueEmail());

		assertThat(mvc.patch()
			.uri("/api/v1/me/profile")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"displayName\": \"Hacked\"}")).hasStatus(HttpStatus.FORBIDDEN);
	}

	// --- Isolation ----------------------------------------------------------------

	@Test
	void eachSessionOnlySeesAndChangesItsOwnAccount() {
		String emailA = uniqueEmail();
		String emailB = uniqueEmail();
		Cookie sessionA = client.registerAndGetSession(emailA);
		Cookie sessionB = new AuthTestClient(mvc).registerAndGetSession(emailB);

		assertThat(patchProfile(sessionB, "{\"displayName\": \"Bea\"}")).hasStatusOk();

		assertThat(client.me(sessionA)).hasStatusOk().bodyJson().extractingPath("$.email").isEqualTo(emailA);
		assertThat(users.findByEmail(emailA).orElseThrow().getDisplayName()).isEqualTo("Test User");
		assertThat(users.findByEmail(emailB).orElseThrow().getDisplayName()).isEqualTo("Bea");
	}

	// --- Profile --------------------------------------------------------------------

	@Test
	void profileUpdateChangesOnlyProvidedFields() {
		Cookie session = client.registerAndGetSession(uniqueEmail());

		assertThat(patchProfile(session, "{\"timeZone\": \"Europe/London\"}")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"displayName\": \"Test User\", \"timeZone\": \"Europe/London\"}");
		assertThat(patchProfile(session, "{\"displayName\": \"  Ana  \"}")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"displayName\": \"Ana\", \"timeZone\": \"Europe/London\"}");
	}

	@Test
	void profileUpdateRejectsInvalidValues() {
		Cookie session = client.registerAndGetSession(uniqueEmail());

		assertThat(patchProfile(session, "{\"timeZone\": \"Nowhere/City\"}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(patchProfile(session, "{\"displayName\": \"   \"}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(patchProfile(session, "{\"displayName\": \"" + "x".repeat(51) + "\"}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	// --- Password change --------------------------------------------------------------

	@Test
	void passwordChangeRequiresCurrentPassword() {
		Cookie session = client.registerAndGetSession(uniqueEmail());

		assertThat(changePassword(session, "not my password at all", NEW_PASSWORD)).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/password-incorrect");
	}

	@Test
	void passwordChangeEnforcesPolicyOnNewPassword() {
		Cookie session = client.registerAndGetSession(uniqueEmail());

		assertThat(changePassword(session, PASSWORD, "too short")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void passwordChangeSignsOutOtherSessionsAndKeepsCurrentOne() {
		String email = uniqueEmail();
		Cookie first = client.registerAndGetSession(email);
		Cookie second = client.loginAndGetSession(email, PASSWORD);

		MvcTestResult result = changePassword(first, PASSWORD, NEW_PASSWORD);
		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
		Cookie renewed = sessionCookie(result);

		assertThat(client.me(second)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(client.me(first)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(client.me(renewed)).hasStatusOk();

		AuthTestClient otherClient = new AuthTestClient(mvc);
		assertThat(otherClient.login(email, PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(otherClient.login(email, NEW_PASSWORD)).hasStatusOk();
	}

	// --- Account deletion ----------------------------------------------------------------

	@Test
	void accountDeletionRequiresPassword() {
		String email = uniqueEmail();
		Cookie session = client.registerAndGetSession(email);

		assertThat(deleteAccount(session, "not my password at all")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(users.findByEmail(email)).isPresent();
		assertThat(client.me(session)).hasStatusOk();
	}

	@Test
	void accountDeletionRemovesAccountAndEndsAllSessions() {
		String email = uniqueEmail();
		Cookie first = client.registerAndGetSession(email);
		Cookie second = client.loginAndGetSession(email, PASSWORD);

		assertThat(deleteAccount(first, PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(users.findByEmail(email)).isEmpty();
		assertThat(client.me(first)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(client.me(second)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(new AuthTestClient(mvc).login(email, PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private MvcTestResult patchProfile(Cookie session, String json) {
		return mvc.patch()
			.uri("/api/v1/me/profile")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.with(client.csrf())
			.with(client.fromClient())
			.exchange();
	}

	private MvcTestResult changePassword(Cookie session, String current, String next) {
		return mvc.put()
			.uri("/api/v1/me/password")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(current, next))
			.with(client.csrf())
			.with(client.fromClient())
			.exchange();
	}

	private MvcTestResult deleteAccount(Cookie session, String password) {
		return mvc.delete()
			.uri("/api/v1/me")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"password\": \"%s\"}".formatted(password))
			.with(client.csrf())
			.with(client.fromClient())
			.exchange();
	}

}
