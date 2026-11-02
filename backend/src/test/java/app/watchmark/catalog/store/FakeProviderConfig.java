package app.watchmark.catalog.store;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import app.watchmark.MutableClock;

/**
 * Replaces the metadata provider and the clock for tests that script provider
 * data and move time explicitly. Shared so those test classes reuse one context.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeProviderConfig {

	@Bean
	@Primary
	FakeMetadataProvider fakeMetadataProvider() {
		return new FakeMetadataProvider();
	}

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock(MetadataSyncTests.NOW);
	}

}
