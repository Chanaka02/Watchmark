package app.watchmark.catalog.tmdb;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * TMDB settings. The token is a secret: it is only read from the environment
 * ({@code TMDB_READ_ACCESS_TOKEN}) and never logged.
 *
 * @param readAccessToken API Read Access Token; blank means TMDB is not configured
 * @param maxRequestsPerSecond client-side pacing, well below TMDB's published
 *     upper limit of roughly 40 requests per second
 * @param posterSize image sizes come from TMDB's {@code /configuration} endpoint;
 *     these defaults are the commonly available ones and are configurable
 */
@Validated
@ConfigurationProperties("watchmark.tmdb")
public record TmdbProperties(String readAccessToken, @NotNull URI baseUrl, @NotNull URI imageBaseUrl,
		@NotBlank String language, @NotNull Duration connectTimeout, @NotNull Duration readTimeout,
		@Positive int maxRequestsPerSecond, @NotBlank String posterSize, @NotBlank String backdropSize,
		@NotBlank String profileSize, @NotBlank String stillSize) {

	public boolean configured() {
		return readAccessToken != null && !readAccessToken.isBlank();
	}

	@Override
	public String toString() {
		return "TmdbProperties[baseUrl=" + baseUrl + ", configured=" + configured() + "]";
	}

}
