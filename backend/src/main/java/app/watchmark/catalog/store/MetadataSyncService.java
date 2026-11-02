package app.watchmark.catalog.store;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

import app.watchmark.catalog.MetadataNotFoundException;
import app.watchmark.catalog.MetadataProvider;
import app.watchmark.catalog.MetadataUnavailableException;
import app.watchmark.catalog.ShowRef;
import app.watchmark.catalog.dto.CatalogDtos.SeasonDetails;
import app.watchmark.catalog.dto.CatalogDtos.SeasonSummary;
import app.watchmark.catalog.dto.CatalogDtos.ShowDetails;
import app.watchmark.catalog.store.MetadataWriter.SeasonState;

/**
 * Copies a show's metadata from the provider into the database.
 *
 * <p>
 * All provider calls happen first, outside any transaction; the result is then
 * written atomically by {@link MetadataWriter}. If any call fails, nothing is
 * written except a failure counter, so stored data is never half-updated.
 */
@Service
public class MetadataSyncService {

	private static final Logger log = LoggerFactory.getLogger(MetadataSyncService.class);

	private final MetadataProvider provider;

	private final MetadataWriter writer;

	private final ShowRepository shows;

	private final RefreshProperties properties;

	private final Clock clock;

	MetadataSyncService(MetadataProvider provider, MetadataWriter writer, ShowRepository shows,
			RefreshProperties properties, Clock clock) {
		this.provider = provider;
		this.writer = writer;
		this.shows = shows;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * The stored show id, syncing the show first if it has never been stored.
	 * Already-stored shows are not refreshed here; the refresh job keeps them current.
	 *
	 * @throws MetadataNotFoundException unknown show, or a ref of another provider
	 * @throws MetadataUnavailableException the provider failed
	 */
	public long ensureStored(ShowRef ref) {
		if (!ref.provider().equals(provider.id())) {
			throw MetadataNotFoundException.show();
		}
		return shows.findByProviderAndExternalId(ref.provider(), ref.externalId())
			.map(Show::getId)
			.orElseGet(() -> sync(ref).showId());
	}

	/**
	 * Fetches and stores the show. Seasons are fetched selectively (see
	 * {@link #needsEpisodes}); a show stored for the first time gets every season.
	 *
	 * @throws MetadataNotFoundException the provider does not know the show and it
	 *     was never stored
	 * @throws MetadataUnavailableException the provider failed; stored data is unchanged
	 */
	public SyncOutcome sync(ShowRef ref) {
		if (!ref.provider().equals(provider.id())) {
			throw MetadataNotFoundException.show();
		}
		Instant now = clock.instant();
		Map<Integer, SeasonState> stored = writer.seasonStates(ref, LocalDate.ofInstant(now, clock.getZone()));

		ShowDetails details;
		try {
			details = provider.show(ref.externalId());
		}
		catch (MetadataNotFoundException ex) {
			// Keep stored data for users' history (R8); it is only marked as missing.
			return writer.markShowMissing(ref, now).map(SyncOutcome::missing).orElseThrow(() -> ex);
		}
		catch (MetadataUnavailableException ex) {
			writer.recordFailure(ref, now);
			throw ex;
		}

		List<SeasonDetails> fetched = new ArrayList<>();
		int latestRegular = details.seasons().stream().mapToInt(SeasonSummary::seasonNumber).max().orElse(0);
		for (SeasonSummary season : details.seasons()) {
			if (!needsEpisodes(season, stored.get(season.seasonNumber()), latestRegular, now)) {
				continue;
			}
			try {
				fetched.add(provider.season(ref.externalId(), season.seasonNumber()));
			}
			catch (MetadataNotFoundException ex) {
				// Listed in the show but not served: leave its stored episodes alone.
				log.warn("Season {} of {} is listed but could not be fetched", season.seasonNumber(), ref);
			}
			catch (MetadataUnavailableException ex) {
				writer.recordFailure(ref, now);
				throw ex;
			}
		}

		try {
			return writer.apply(ref, details, fetched, now);
		}
		catch (DataIntegrityViolationException | PessimisticLockingFailureException ex) {
			// Another sync stored this show at the same moment (duplicate key), or both
			// took gap locks on the missing row and MySQL chose this one as deadlock
			// victim. Either way the other sync has committed or will; apply on top of it.
			log.info("Concurrent sync of {}; retrying once", ref);
			return writer.apply(ref, details, fetched, now);
		}
	}

	/**
	 * A season's episodes are fetched when anything suggests they changed or may
	 * change: never fetched, a different episode count, the latest season,
	 * episodes still pending (no date, or airing from yesterday on), or simply
	 * not fetched for {@code season-max-age}.
	 */
	boolean needsEpisodes(SeasonSummary season, SeasonState stored, int latestSeasonNumber, Instant now) {
		if (stored == null || stored.episodesSyncedAt() == null) {
			return true;
		}
		if (!Objects.equals(stored.episodeCount(), season.episodeCount())) {
			return true;
		}
		if (season.seasonNumber() == latestSeasonNumber || stored.hasPendingEpisodes()) {
			return true;
		}
		return Duration.between(stored.episodesSyncedAt(), now).compareTo(properties.seasonMaxAge()) > 0;
	}

}
