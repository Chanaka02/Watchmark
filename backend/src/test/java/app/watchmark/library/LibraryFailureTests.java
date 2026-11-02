package app.watchmark.library;

import static app.watchmark.auth.AuthTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import app.watchmark.TestcontainersConfiguration;
import app.watchmark.auth.AuthTestClient;
import app.watchmark.catalog.MetadataUnavailableException;
import app.watchmark.catalog.store.MetadataSyncService;

/** Adding a show while the metadata provider is down. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class LibraryFailureTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	MetadataSyncService metadata;

	@Test
	void providerOutageIsA503AndCreatesNoEntry() {
		given(metadata.ensureStored(any())).willThrow(new MetadataUnavailableException(Duration.ofSeconds(20), null));
		AuthTestClient client = new AuthTestClient(mvc);
		String email = uniqueEmail();
		var session = client.registerAndGetSession(email);

		assertThat(mvc.put()
			.uri("/api/v1/library/mock:1001")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\": \"WATCHING\"}")
			.with(client.csrf())
			.with(client.fromClient())).hasStatus(HttpStatus.SERVICE_UNAVAILABLE).headers().hasValue("Retry-After", "20");

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_shows us JOIN users u ON u.id = us.user_id "
				+ "WHERE u.email = ?", Integer.class, email)).isZero();
	}

}
