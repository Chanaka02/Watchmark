package app.watchmark.catalog.store;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import app.watchmark.catalog.DiscoverList;
import app.watchmark.catalog.MetadataNotFoundException;
import app.watchmark.catalog.MetadataProvider;
import app.watchmark.catalog.MetadataUnavailableException;
import app.watchmark.catalog.dto.CatalogDtos.ResultPage;
import app.watchmark.catalog.dto.CatalogDtos.SeasonDetails;
import app.watchmark.catalog.dto.CatalogDtos.ShowDetails;
import app.watchmark.catalog.dto.CatalogDtos.ShowSummary;

/** Scriptable provider for sync tests: data, failures and call counts per show. */
public class FakeMetadataProvider implements MetadataProvider {

	public final Map<String, ShowDetails> shows = new ConcurrentHashMap<>();

	public final Map<String, SeasonDetails> seasons = new ConcurrentHashMap<>();

	/** Keys ({@code "show"} or {@code "show/season"}) that fail with 503. */
	public final Map<String, Boolean> unavailable = new ConcurrentHashMap<>();

	final List<String> calls = Collections.synchronizedList(new ArrayList<>());

	final AtomicInteger showCalls = new AtomicInteger();

	public void reset() {
		shows.clear();
		seasons.clear();
		unavailable.clear();
		calls.clear();
		showCalls.set(0);
	}

	@Override
	public String id() {
		return "tmdb";
	}

	@Override
	public boolean isMock() {
		return false;
	}

	@Override
	public ResultPage<ShowSummary> discover(DiscoverList list, int page) {
		return ResultPage.empty(page);
	}

	@Override
	public ResultPage<ShowSummary> search(String query, int page) {
		return ResultPage.empty(page);
	}

	@Override
	public ShowDetails show(String externalId) {
		calls.add(externalId);
		showCalls.incrementAndGet();
		if (unavailable.containsKey(externalId)) {
			throw new MetadataUnavailableException(Duration.ofSeconds(5), null);
		}
		ShowDetails show = shows.get(externalId);
		if (show == null) {
			throw MetadataNotFoundException.show();
		}
		return show;
	}

	@Override
	public SeasonDetails season(String externalId, int seasonNumber) {
		String key = externalId + "/" + seasonNumber;
		calls.add(key);
		if (unavailable.containsKey(key)) {
			throw new MetadataUnavailableException(null, null);
		}
		SeasonDetails season = seasons.get(key);
		if (season == null) {
			throw MetadataNotFoundException.season();
		}
		return season;
	}

	List<String> seasonCalls(String externalId) {
		return calls.stream().filter(call -> call.startsWith(externalId + "/")).toList();
	}

}
