package app.watchmark.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import app.watchmark.catalog.mock.MockMetadataProvider;
import app.watchmark.catalog.tmdb.TmdbMetadataProvider;
import app.watchmark.catalog.tmdb.TmdbProperties;

class ShowRefAndConfigTests {

	@Test
	void parsesProviderQualifiedRefs() {
		assertThat(ShowRef.parse("tmdb:1399")).contains(new ShowRef("tmdb", "1399"));
		assertThat(new ShowRef("mock", "1001")).hasToString("mock:1001");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "1399", "tmdb:", "tmdb:0", "tmdb:-1", "tmdb:12a", "TMDB:1", "tmdb:1:2",
			"tmdb:1234567890123", "../etc:1" })
	void rejectsMalformedRefs(String value) {
		assertThat(ShowRef.parse(value)).isEmpty();
	}

	// --- Provider selection ---------------------------------------------------------

	private final MetadataConfig config = new MetadataConfig();

	@Test
	void autoUsesMockWithoutTokenAndTmdbWithToken() {
		assertThat(select("auto", "")).isInstanceOf(MockMetadataProvider.class);
		assertThat(select("auto", "a-token")).isInstanceOf(TmdbMetadataProvider.class);
	}

	@Test
	void explicitTmdbWithoutTokenFailsFast() {
		assertThatThrownBy(() -> select("tmdb", " ")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("TMDB_READ_ACCESS_TOKEN");
	}

	@Test
	void explicitMockIgnoresToken() {
		assertThat(select("mock", "a-token")).isInstanceOf(MockMetadataProvider.class);
	}

	@Test
	void unknownSelectionFails() {
		assertThatThrownBy(() -> select("imdb", "")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void tokenNeverAppearsInPropertiesToString() {
		assertThat(properties("super-secret-token")).asString().doesNotContain("super-secret-token");
	}

	private MetadataProvider select(String selection, String token) {
		return config.metadataProvider(selection, properties(token), RestClient.builder(), Clock.systemUTC());
	}

	private static TmdbProperties properties(String token) {
		return new TmdbProperties(token, URI.create("https://api.themoviedb.org/3"),
				URI.create("https://image.tmdb.org/t/p"), "en-US", Duration.ofSeconds(3), Duration.ofSeconds(5), 20,
				"w342", "w1280", "w185", "w300");
	}

}
