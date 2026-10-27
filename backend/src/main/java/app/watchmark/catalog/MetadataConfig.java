package app.watchmark.catalog;

import java.time.Clock;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import app.watchmark.catalog.mock.MockMetadataProvider;
import app.watchmark.catalog.tmdb.TmdbConfig;
import app.watchmark.catalog.tmdb.TmdbProperties;

/**
 * Chooses the single active {@link MetadataProvider} from
 * {@code watchmark.metadata.provider}:
 * <ul>
 * <li>{@code tmdb}: TMDB; startup fails if no token is configured.</li>
 * <li>{@code mock}: fictional development data.</li>
 * <li>{@code auto} (default): TMDB when a token is set, otherwise mock.</li>
 * </ul>
 * Production must set {@code METADATA_PROVIDER=tmdb} so a missing token can
 * never silently fall back to mock data.
 */
@Configuration(proxyBeanMethods = false)
class MetadataConfig {

	private static final Logger log = LoggerFactory.getLogger(MetadataConfig.class);

	@Bean
	MetadataProvider metadataProvider(@Value("${watchmark.metadata.provider}") String selection,
			TmdbProperties tmdb, RestClient.Builder restClientBuilder, Clock clock) {
		return switch (selection.strip().toLowerCase(Locale.ROOT)) {
			case "tmdb" -> {
				if (!tmdb.configured()) {
					throw new IllegalStateException(
							"watchmark.metadata.provider=tmdb but TMDB_READ_ACCESS_TOKEN is not set");
				}
				yield TmdbConfig.provider(restClientBuilder, tmdb);
			}
			case "mock" -> mock(clock);
			case "auto" -> tmdb.configured() ? TmdbConfig.provider(restClientBuilder, tmdb) : mock(clock);
			default -> throw new IllegalStateException(
					"watchmark.metadata.provider must be tmdb, mock or auto, not '" + selection + "'");
		};
	}

	private static MetadataProvider mock(Clock clock) {
		log.warn("Using MOCK metadata (fictional shows). Set TMDB_READ_ACCESS_TOKEN to use TMDB.");
		return new MockMetadataProvider(clock);
	}

}
