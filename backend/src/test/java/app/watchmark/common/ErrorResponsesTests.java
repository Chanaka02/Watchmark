package app.watchmark.common;

import static app.watchmark.auth.AuthTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import app.watchmark.TestcontainersConfiguration;
import app.watchmark.auth.AuthTestClient;

/**
 * Every error is RFC 9457 problem+json with a stable type, and none reveals
 * internals (stack traces, exception classes or messages, parser details).
 */
@Import({ TestcontainersConfiguration.class, ErrorResponsesTests.Failing.class })
@SpringBootTest
@AutoConfigureMockMvc
class ErrorResponsesTests {

	/** A test-only endpoint that fails unexpectedly, with a message that must not leak. */
	@TestConfiguration(proxyBeanMethods = false)
	@RestController
	static class Failing {

		@GetMapping("/api/v1/test-only/explode")
		String explode() {
			throw new IllegalStateException("secret internal detail: db password=hunter2");
		}

	}

	@Autowired
	MockMvcTester mvc;

	AuthTestClient client;

	Cookie session;

	@BeforeEach
	void signIn() {
		client = new AuthTestClient(mvc);
		session = client.registerAndGetSession(uniqueEmail());
	}

	@Test
	void unknownApiPathsAreProblemNotFound() {
		MvcTestResult result = mvc.get().uri("/api/v1/no-such-thing").cookie(session).with(client.fromClient()).exchange();

		assertProblem(result, HttpStatus.NOT_FOUND, "/problems/not-found");
		assertThat(body(result)).doesNotContain("static resource");
	}

	@Test
	void unknownApiPathsNeedSignInFirst() {
		assertProblem(mvc.get().uri("/api/v1/no-such-thing").exchange(), HttpStatus.UNAUTHORIZED,
				"/problems/unauthenticated");
	}

	@Test
	void wrongMethodsAreProblemMethodNotAllowed() {
		MvcTestResult result = mvc.delete()
			.uri("/api/v1/me/dashboard")
			.cookie(session)
			.with(client.csrf())
			.with(client.fromClient())
			.exchange();

		assertProblem(result, HttpStatus.METHOD_NOT_ALLOWED, "/problems/method-not-allowed");
	}

	@Test
	void malformedJsonDoesNotEchoParserDetails() {
		MvcTestResult result = mvc.put()
			.uri("/api/v1/library/mock:1001")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\": ")
			.with(client.csrf())
			.with(client.fromClient())
			.exchange();

		assertProblem(result, HttpStatus.BAD_REQUEST, "/problems/bad-request");
		assertThat(body(result)).doesNotContainIgnoringCase("jackson").doesNotContainIgnoringCase("line:");
	}

	@Test
	void wrongParameterTypesAreBadRequest() {
		MvcTestResult result = mvc.put()
			.uri("/api/v1/tracking/episodes/not-a-number/watch")
			.cookie(session)
			.with(client.csrf())
			.with(client.fromClient())
			.exchange();

		assertProblem(result, HttpStatus.BAD_REQUEST, "/problems/bad-request");
	}

	@Test
	void unexpectedFailuresRevealNothing() {
		MvcTestResult result = mvc.get()
			.uri("/api/v1/test-only/explode")
			.cookie(session)
			.with(client.fromClient())
			.exchange();

		assertProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, "/problems/internal-error");
		assertThat(body(result)).doesNotContain("hunter2").doesNotContain("IllegalStateException");
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String type) {
		assertThat(result).hasStatus(status);
		assertThat(result).contentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(type);
		assertThat(body(result)).doesNotContain("\"trace\"")
			.doesNotContain("\"exception\"")
			.doesNotContain("at app.watchmark")
			.doesNotContain("org.springframework");
	}

	private static String body(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
