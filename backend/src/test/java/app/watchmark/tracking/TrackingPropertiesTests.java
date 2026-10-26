package app.watchmark.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class TrackingPropertiesTests {

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(TrackingProperties.class)
	static class Config {

	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

	@Test
	void bindsConfiguredDurations() {
		runner.withPropertyValues("watchmark.tracking.undo-window=12h",
				"watchmark.tracking.soft-delete-retention=60d", "watchmark.tracking.purge-enabled=false")
			.run(context -> {
				var props = context.getBean(TrackingProperties.class);
				assertThat(props.undoWindow()).isEqualTo(Duration.ofHours(12));
				assertThat(props.softDeleteRetention()).isEqualTo(Duration.ofDays(60));
				assertThat(props.purgeEnabled()).isFalse();
			});
	}

	@Test
	void acceptsRetentionEqualToUndoWindow() {
		runner.withPropertyValues("watchmark.tracking.undo-window=24h",
				"watchmark.tracking.soft-delete-retention=1d", "watchmark.tracking.purge-enabled=true")
			.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void rejectsRetentionShorterThanUndoWindow() {
		runner.withPropertyValues("watchmark.tracking.undo-window=48h",
				"watchmark.tracking.soft-delete-retention=1d")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void rejectsNonPositiveUndoWindow() {
		runner.withPropertyValues("watchmark.tracking.undo-window=0s",
				"watchmark.tracking.soft-delete-retention=30d")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void rejectsMissingValues() {
		runner.run(context -> assertThat(context).hasFailed());
	}

}
