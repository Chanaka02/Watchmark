package app.watchmark.catalog;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-memory caches for provider responses. They cut latency and keep us far
 * below TMDB's rate limits; every TTL is far inside TMDB's six-month caching
 * limit. Failures are never cached (exceptions bypass {@code @Cacheable}).
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
class CatalogCacheConfig {

	static final String LISTS = "catalog-lists";

	static final String SEARCH = "catalog-search";

	static final String SHOWS = "catalog-shows";

	static final String SEASONS = "catalog-seasons";

	@Bean
	CacheManager cacheManager() {
		var manager = new CaffeineCacheManager();
		manager.setAllowNullValues(false);
		manager.registerCustomCache(LISTS, cache(Duration.ofHours(1), 500));
		manager.registerCustomCache(SEARCH, cache(Duration.ofMinutes(15), 2_000));
		manager.registerCustomCache(SHOWS, cache(Duration.ofHours(6), 2_000));
		manager.registerCustomCache(SEASONS, cache(Duration.ofHours(6), 5_000));
		return manager;
	}

	private static Cache<Object, Object> cache(Duration ttl, long maxEntries) {
		return Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxEntries).build();
	}

}
