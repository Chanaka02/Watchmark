package app.watchmark.catalog.tmdb;

import java.net.http.HttpClient;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Builds the TMDB provider; called by MetadataConfig only when TMDB is selected. */
public final class TmdbConfig {

	private TmdbConfig() {
	}

	public static TmdbMetadataProvider provider(RestClient.Builder builder, TmdbProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(properties.connectTimeout())
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
		var requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());

		RestClient restClient = builder.clone()
			.baseUrl(properties.baseUrl().toString())
			.requestFactory(requestFactory)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.readAccessToken().strip())
			.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.build();
		return new TmdbMetadataProvider(restClient, properties, new RequestPacer(properties.maxRequestsPerSecond()));
	}

}
