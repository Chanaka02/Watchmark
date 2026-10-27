package app.watchmark.catalog.tmdb;

import java.net.URI;
import java.time.Duration;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import app.watchmark.catalog.DiscoverList;
import app.watchmark.catalog.MetadataNotFoundException;
import app.watchmark.catalog.MetadataProvider;
import app.watchmark.catalog.MetadataUnavailableException;
import app.watchmark.catalog.dto.CatalogDtos.ResultPage;
import app.watchmark.catalog.dto.CatalogDtos.SeasonDetails;
import app.watchmark.catalog.dto.CatalogDtos.ShowDetails;
import app.watchmark.catalog.dto.CatalogDtos.ShowSummary;

/**
 * TMDB v3 implementation of {@link MetadataProvider}.
 *
 * <p>
 * Failures never leak upstream details to clients: a missing show or season
 * becomes {@link MetadataNotFoundException}; everything else (timeouts, 429,
 * 5xx, rejected token, unreadable JSON) becomes
 * {@link MetadataUnavailableException} and is logged here without the token.
 */
public class TmdbMetadataProvider implements MetadataProvider {

	private static final Logger log = LoggerFactory.getLogger(TmdbMetadataProvider.class);

	private final RestClient restClient;

	private final TmdbProperties properties;

	private final RequestPacer pacer;

	private final TmdbMapper mapper;

	/**
	 * @param restClient preconfigured with base URL, bearer token and timeouts (see
	 *     TmdbConfig)
	 */
	TmdbMetadataProvider(RestClient restClient, TmdbProperties properties, RequestPacer pacer) {
		this.restClient = restClient;
		this.properties = properties;
		this.pacer = pacer;
		this.mapper = new TmdbMapper(properties);
	}

	@Override
	public String id() {
		return TmdbMapper.PROVIDER;
	}

	@Override
	public boolean isMock() {
		return false;
	}

	@Override
	public ResultPage<ShowSummary> discover(DiscoverList list, int page) {
		String path = switch (list) {
			case TRENDING -> "/trending/tv/week";
			case POPULAR -> "/tv/popular";
			case TOP_RATED -> "/tv/top_rated";
			case ON_THE_AIR -> "/tv/on_the_air";
		};
		var body = get(uri -> uri.path(path).queryParam("page", page).queryParam("language", properties.language()).build(),
				TmdbResponses.Page.class, null);
		return mapper.page(body, page);
	}

	@Override
	public ResultPage<ShowSummary> search(String query, int page) {
		var body = get(uri -> uri.path("/search/tv")
			.queryParam("query", "{query}")
			.queryParam("include_adult", false)
			.queryParam("page", page)
			.queryParam("language", properties.language())
			.build(query), TmdbResponses.Page.class, null);
		return mapper.page(body, page);
	}

	@Override
	public ShowDetails show(String externalId) {
		var body = get(uri -> uri.path("/tv/{id}")
			.queryParam("append_to_response", "credits")
			.queryParam("language", properties.language())
			.build(externalId), TmdbResponses.ShowDetails.class, MetadataNotFoundException.show());
		if (body.id() == null || Boolean.TRUE.equals(body.adult())) {
			throw MetadataNotFoundException.show();
		}
		return mapper.details(body);
	}

	@Override
	public SeasonDetails season(String externalId, int seasonNumber) {
		var body = get(uri -> uri.path("/tv/{id}/season/{season}")
			.queryParam("language", properties.language())
			.build(externalId, seasonNumber), TmdbResponses.Season.class, MetadataNotFoundException.season());
		return mapper.season(externalId, seasonNumber, body);
	}

	/**
	 * @param notFound thrown on HTTP 404; {@code null} treats 404 as a provider
	 *     failure (lists and search should never 404)
	 */
	private <T> T get(Function<UriBuilder, URI> uri, Class<T> type, MetadataNotFoundException notFound) {
		try {
			pacer.acquire();
			T body = restClient.get().uri(uri).retrieve().body(type);
			if (body == null) {
				log.warn("TMDB returned an empty body for {}", type.getSimpleName());
				throw new MetadataUnavailableException(null, null);
			}
			return body;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new MetadataUnavailableException(null, ex);
		}
		catch (RestClientResponseException ex) {
			HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
			if (status == HttpStatus.NOT_FOUND && notFound != null) {
				throw notFound;
			}
			if (status == HttpStatus.TOO_MANY_REQUESTS) {
				Duration retryAfter = retryAfter(ex.getResponseHeaders());
				log.warn("TMDB rate limit reached; retry after {}", retryAfter);
				throw new MetadataUnavailableException(retryAfter, ex);
			}
			if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
				log.error("TMDB rejected the request with {}: check TMDB_READ_ACCESS_TOKEN", ex.getStatusCode());
			}
			else {
				log.warn("TMDB request failed with {}", ex.getStatusCode());
			}
			throw new MetadataUnavailableException(null, ex);
		}
		catch (ResourceAccessException ex) {
			log.warn("TMDB could not be reached: {}", ex.getMostSpecificCause().getClass().getSimpleName());
			throw new MetadataUnavailableException(null, ex);
		}
		catch (RestClientException ex) {
			log.warn("TMDB response could not be read: {}", ex.getMostSpecificCause().getClass().getSimpleName());
			throw new MetadataUnavailableException(null, ex);
		}
	}

	private static Duration retryAfter(HttpHeaders headers) {
		String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
		if (value == null) {
			return null;
		}
		try {
			long seconds = Long.parseLong(value.strip());
			return seconds > 0 ? Duration.ofSeconds(Math.min(seconds, 3600)) : null;
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

}
