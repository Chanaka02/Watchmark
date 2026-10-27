package app.watchmark.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import jakarta.servlet.http.Cookie;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Small HTTP helper for auth integration tests.
 * <ul>
 * <li>Every instance uses its own random client IP so tests never share
 * rate-limit buckets.</li>
 * <li>CSRF uses the real double-submit flow ({@code GET /auth/csrf}, then cookie
 * plus header). Spring Security's {@code csrf()} test post-processor is avoided
 * on purpose: it replaces the CSRF repository inside the shared application
 * context, which silently changes behaviour for later tests.</li>
 * </ul>
 */
public final class AuthTestClient {

	public static final String SESSION_COOKIE = "WMSESSION";

	public static final String PASSWORD = "correct horse battery staple";

	private final MockMvcTester mvc;

	private final String clientIp;

	private Cookie xsrfToken;

	public AuthTestClient(MockMvcTester mvc) {
		this.mvc = mvc;
		var random = ThreadLocalRandom.current();
		this.clientIp = "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
	}

	public RequestPostProcessor fromClient() {
		return request -> {
			request.setRemoteAddr(clientIp);
			return request;
		};
	}

	/** Adds a valid XSRF-TOKEN cookie and matching X-XSRF-TOKEN header. */
	public RequestPostProcessor csrf() {
		return request -> {
			if (xsrfToken == null) {
				xsrfToken = mvc.get().uri("/api/v1/auth/csrf").exchange().getResponse().getCookie("XSRF-TOKEN");
				assertThat(xsrfToken).as("XSRF-TOKEN cookie").isNotNull();
			}
			Cookie[] existing = request.getCookies() == null ? new Cookie[0] : request.getCookies();
			request.setCookies(Stream.concat(Arrays.stream(existing), Stream.of(xsrfToken)).toArray(Cookie[]::new));
			request.addHeader("X-XSRF-TOKEN", xsrfToken.getValue());
			return request;
		};
	}

	public static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	public MvcTestResult register(String email, String password) {
		return mvc.post()
			.uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s", "password": "%s", "displayName": "Test User", "timeZone": "Asia/Colombo"}
					""".formatted(email, password))
			.with(csrf())
			.with(fromClient())
			.exchange();
	}

	/** Registers a new account and returns its session cookie. */
	public Cookie registerAndGetSession(String email) {
		MvcTestResult result = register(email, PASSWORD);
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return sessionCookie(result);
	}

	public MvcTestResult login(String email, String password, Cookie... cookies) {
		var request = mvc.post()
			.uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s", "password": "%s"}
					""".formatted(email, password))
			.with(csrf())
			.with(fromClient());
		if (cookies.length > 0) {
			request.cookie(cookies);
		}
		return request.exchange();
	}

	public Cookie loginAndGetSession(String email, String password) {
		MvcTestResult result = login(email, password);
		assertThat(result).hasStatusOk();
		return sessionCookie(result);
	}

	public MvcTestResult me(Cookie session) {
		return mvc.get().uri("/api/v1/auth/me").cookie(session).with(fromClient()).exchange();
	}

	public static Cookie sessionCookie(MvcTestResult result) {
		Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
		assertThat(cookie).as("session cookie").isNotNull();
		assertThat(cookie.getValue()).isNotEmpty();
		return cookie;
	}

}
