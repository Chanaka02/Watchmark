package app.watchmark.auth;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import app.watchmark.TestcontainersConfiguration;
import app.watchmark.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	AuthTestClient client;

	@BeforeEach
	void setUp() {
		client = new AuthTestClient(mvc);
	}

	// --- Registration ---------------------------------------------------------

	@Test
	void registrationCreatesAccountAndSignsIn() throws Exception {
		String email = uniqueEmail();
		MvcTestResult result = client.register(email, PASSWORD);

		assertThat(result).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"email": "%s", "displayName": "Test User", "timeZone": "Asia/Colombo"}
					""".formatted(email));
		assertThat(result.getResponse().getContentAsString()).doesNotContain("password", PASSWORD);

		assertThat(client.me(sessionCookie(result))).hasStatusOk()
			.bodyJson()
			.extractingPath("$.email")
			.isEqualTo(email);
	}

	@Test
	void passwordIsStoredAsBcryptHashNeverPlainText() {
		String email = uniqueEmail();
		client.registerAndGetSession(email);

		String hash = users.findByEmail(email).orElseThrow().getPasswordHash();
		assertThat(hash).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
	}

	@Test
	void emailIsNormalisedAndDuplicatesAreRejectedCaseInsensitively() {
		String email = uniqueEmail();
		client.registerAndGetSession(email.toUpperCase());

		assertThat(users.findByEmail(email)).isPresent();
		assertThat(client.register(email, PASSWORD)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/email-taken");
	}

	@Test
	void registrationValidatesInputWithoutEchoingPassword() throws Exception {
		MvcTestResult result = mvc.post()
			.uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "not-an-email", "password": "short-secret", "displayName": " ", "timeZone": "+05:30"}
					""")
			.with(client.csrf())
			.with(client.fromClient())
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.errors[*].field")
			.asArray()
			.containsExactlyInAnyOrder("email", "password", "displayName", "timeZone");
		assertThat(result.getResponse().getContentAsString()).doesNotContain("short-secret");
	}

	// --- Login ----------------------------------------------------------------

	@Test
	void loginWithCorrectCredentialsStartsSession() {
		String email = uniqueEmail();
		client.registerAndGetSession(email);

		MvcTestResult result = client.login(email.toUpperCase(), PASSWORD);
		assertThat(result).hasStatusOk().bodyJson().extractingPath("$.email").isEqualTo(email);
		assertThat(client.me(sessionCookie(result))).hasStatusOk();
	}

	@Test
	void unknownEmailAndWrongPasswordGiveIdenticalResponses() throws Exception {
		String email = uniqueEmail();
		client.registerAndGetSession(email);

		MvcTestResult wrongPassword = client.login(email, "wrong password, wrong password");
		MvcTestResult unknownEmail = client.login(uniqueEmail(), PASSWORD);

		assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(unknownEmail).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(wrongPassword.getResponse().getContentAsString())
			.isEqualTo(unknownEmail.getResponse().getContentAsString())
			.contains("/problems/invalid-credentials");
		assertThat(wrongPassword.getResponse().getCookie(AuthTestClient.SESSION_COOKIE)).isNull();
	}

	@Test
	void overlongPasswordIsRejectedAsInvalidCredentialsNotServerError() {
		String email = uniqueEmail();
		client.registerAndGetSession(email);

		assertThat(client.login(email, "a".repeat(200))).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void loginIssuesNewSessionIdToPreventFixation() {
		String email = uniqueEmail();
		Cookie before = client.registerAndGetSession(email);

		MvcTestResult result = client.login(email, PASSWORD, before);
		Cookie after = sessionCookie(result);

		assertThat(after.getValue()).isNotEqualTo(before.getValue());
		assertThat(client.me(before)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(client.me(after)).hasStatusOk();
	}

	@Test
	void sessionCookieIsHttpOnlyAndSameSiteLax() {
		MvcTestResult result = client.register(uniqueEmail(), PASSWORD);

		assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
			.anySatisfy(header -> assertThat(header).startsWith("WMSESSION=")
				.contains("HttpOnly")
				.contains("SameSite=Lax")
				.contains("Path=/")
				.contains("Max-Age=2592000"));
	}

	@Test
	void sessionIsStoredServerSideAndIndexedByUserId() {
		String email = uniqueEmail();
		client.registerAndGetSession(email);
		long userId = users.findByEmail(email).orElseThrow().getId();

		Integer sessions = jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?",
				Integer.class, Long.toString(userId));
		assertThat(sessions).isEqualTo(1);

		// 14-day idle timeout from spring.session.timeout.
		Integer maxInactiveSeconds = jdbc.queryForObject(
				"SELECT MAX_INACTIVE_INTERVAL FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", Integer.class,
				Long.toString(userId));
		assertThat(maxInactiveSeconds).isEqualTo(14 * 24 * 60 * 60);
	}

	@Test
	void repeatedFailedLoginsAreRateLimitedPerClientAndAccount() {
		String email = uniqueEmail();
		client.registerAndGetSession(email);

		for (int i = 0; i < 5; i++) {
			assertThat(client.login(email, "wrong password, wrong password")).hasStatus(HttpStatus.UNAUTHORIZED);
		}
		MvcTestResult limited = client.login(email, PASSWORD);
		assertThat(limited).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/rate-limited");
		assertThat(Long.parseLong(limited.getResponse().getHeader(HttpHeaders.RETRY_AFTER))).isPositive();

		// The same account from another client is not locked out.
		assertThat(new AuthTestClient(mvc).login(email, PASSWORD)).hasStatusOk();
	}

	// --- Logout ---------------------------------------------------------------

	@Test
	void logoutEndsTheSession() {
		Cookie session = client.registerAndGetSession(uniqueEmail());

		assertThat(mvc.post().uri("/api/v1/auth/logout").cookie(session).with(client.csrf()).with(client.fromClient()))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(client.me(session)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// --- CSRF -----------------------------------------------------------------

	@Test
	void csrfEndpointSetsReadableTokenCookie() {
		MvcTestResult result = mvc.get().uri("/api/v1/auth/csrf").exchange();

		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
		Cookie token = result.getResponse().getCookie("XSRF-TOKEN");
		assertThat(token).isNotNull();
		assertThat(token.isHttpOnly()).isFalse();
		assertThat(token.getAttribute("SameSite")).isEqualTo("Lax");
		assertThat(token.getPath()).isEqualTo("/");
	}

	@Test
	void stateChangingRequestWithMatchingCsrfHeaderSucceeds() {
		Cookie token = mvc.get().uri("/api/v1/auth/csrf").exchange().getResponse().getCookie("XSRF-TOKEN");

		MvcTestResult result = mvc.post()
			.uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s", "password": "%s", "displayName": "Csrf", "timeZone": "UTC"}
					""".formatted(uniqueEmail(), PASSWORD))
			.cookie(token)
			.header("X-XSRF-TOKEN", token.getValue())
			.with(client.fromClient())
			.exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void stateChangingRequestWithoutCsrfTokenIsRejected() {
		MvcTestResult result = mvc.post()
			.uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"a@example.com\", \"password\": \"x\"}")
			.with(client.fromClient())
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("/problems/csrf-token-invalid");
	}

	@Test
	void csrfHeaderNotMatchingCookieIsRejected() {
		Cookie token = mvc.get().uri("/api/v1/auth/csrf").exchange().getResponse().getCookie("XSRF-TOKEN");

		MvcTestResult result = mvc.post()
			.uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"a@example.com\", \"password\": \"x\"}")
			.cookie(token)
			.header("X-XSRF-TOKEN", "forged-token")
			.with(client.fromClient())
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

}
