package app.watchmark.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import app.watchmark.TestcontainersConfiguration;

/** Guards the documented defaults in docs/tracking-rules.md against accidental change. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TrackingDefaultsTests {

	@Autowired
	TrackingProperties properties;

	@Test
	void shipsWithDocumentedDefaults() {
		assertThat(properties.undoWindow()).isEqualTo(Duration.ofHours(24));
		assertThat(properties.softDeleteRetention()).isEqualTo(Duration.ofDays(30));
		assertThat(properties.purgeEnabled()).isTrue();
	}

}
