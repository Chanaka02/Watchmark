package app.watchmark.catalog;

import app.watchmark.catalog.dto.CatalogDtos.ResultPage;
import app.watchmark.catalog.dto.CatalogDtos.SeasonDetails;
import app.watchmark.catalog.dto.CatalogDtos.ShowDetails;
import app.watchmark.catalog.dto.CatalogDtos.ShowSummary;

/**
 * Source of TV metadata. Exactly one implementation is active (see
 * MetadataConfig). Implementations must tolerate missing fields and must never
 * invent values: unknown data stays {@code null}.
 *
 * @see MetadataNotFoundException
 * @see MetadataUnavailableException
 */
public interface MetadataProvider {

	/** Short id used as the {@link ShowRef} prefix, e.g. {@code tmdb}. */
	String id();

	/** Whether the data is fictional development data. */
	boolean isMock();

	ResultPage<ShowSummary> discover(DiscoverList list, int page);

	ResultPage<ShowSummary> search(String query, int page);

	ShowDetails show(String externalId);

	SeasonDetails season(String externalId, int seasonNumber);

}
