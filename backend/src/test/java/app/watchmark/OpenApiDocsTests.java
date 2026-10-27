package app.watchmark;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The OpenAPI document works when enabled (it is off by default; see WatchmarkBackendApplicationTests). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "springdoc.api-docs.enabled=true" })
@AutoConfigureMockMvc
class OpenApiDocsTests {

	@Autowired
	MockMvcTester mvc;

	@Test
	void documentsTheApi() {
		assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk()
			.bodyText()
			.contains("/api/v1/catalog/search", "/api/v1/auth/login", "/api/v1/me/profile");
	}

}
