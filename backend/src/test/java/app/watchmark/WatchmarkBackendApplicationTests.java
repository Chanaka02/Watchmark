package app.watchmark;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class WatchmarkBackendApplicationTests {

	@Autowired
	Flyway flyway;

	@Autowired
	MockMvcTester mvc;

	@Test
	void flywayAppliesAllMigrationsAgainstMysql() {
		assertThat(flyway.info().current()).isNotNull();
		assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
		assertThat(flyway.info().pending()).isEmpty();
	}

	@Test
	void healthEndpointIsPublicUpAndHidesDetails() {
		// Probe group names ("liveness", "readiness") may be listed; component
		// details such as the database must never be exposed anonymously.
		assertThat(mvc.get().uri("/actuator/health")).hasStatusOk()
			.bodyJson()
			.satisfies(json -> {
				assertThat(json.getJson()).doesNotContain("components", "details", "mysql");
			})
			.extractingPath("$.status")
			.isEqualTo("UP");
	}

	@Test
	void otherActuatorEndpointsAreNotPublic() {
		assertThat(mvc.get().uri("/actuator/env")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

}
