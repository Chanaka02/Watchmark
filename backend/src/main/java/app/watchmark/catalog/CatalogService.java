package app.watchmark.catalog;

import java.util.Locale;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import app.watchmark.catalog.dto.CatalogDtos.CatalogSource;
import app.watchmark.catalog.dto.CatalogDtos.ResultPage;
import app.watchmark.catalog.dto.CatalogDtos.SeasonDetails;
import app.watchmark.catalog.dto.CatalogDtos.ShowDetails;
import app.watchmark.catalog.dto.CatalogDtos.ShowSummary;

/** Read-only access to show metadata through the active provider, with caching. */
@Service
public class CatalogService {

	private final MetadataProvider provider;

	public CatalogService(MetadataProvider provider) {
		this.provider = provider;
	}

	public CatalogSource source() {
		return new CatalogSource(provider.id(), provider.isMock());
	}

	@Cacheable(cacheNames = CatalogCacheConfig.LISTS, key = "#list.name() + ':' + #page")
	public ResultPage<ShowSummary> discover(DiscoverList list, int page) {
		return provider.discover(list, page);
	}

	/** {@code query} must already be normalised with {@link #normalizeQuery}. */
	@Cacheable(cacheNames = CatalogCacheConfig.SEARCH, key = "#query + ':' + #page")
	public ResultPage<ShowSummary> search(String query, int page) {
		return provider.search(query, page);
	}

	@Cacheable(cacheNames = CatalogCacheConfig.SHOWS, key = "#ref.toString()")
	public ShowDetails show(ShowRef ref) {
		requireActiveProvider(ref);
		return provider.show(ref.externalId());
	}

	@Cacheable(cacheNames = CatalogCacheConfig.SEASONS, key = "#ref.toString() + ':' + #seasonNumber")
	public SeasonDetails season(ShowRef ref, int seasonNumber) {
		requireActiveProvider(ref);
		return provider.season(ref.externalId(), seasonNumber);
	}

	/** Trims, collapses whitespace and lowercases so equivalent searches share a cache entry. */
	public static String normalizeQuery(String query) {
		return query.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private void requireActiveProvider(ShowRef ref) {
		if (!ref.provider().equals(provider.id())) {
			throw MetadataNotFoundException.show();
		}
	}

}
