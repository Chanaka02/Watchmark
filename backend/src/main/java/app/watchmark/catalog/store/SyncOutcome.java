package app.watchmark.catalog.store;

/**
 * Result of one metadata sync.
 *
 * @param showId stored show id
 * @param created the show was stored for the first time
 * @param upstreamMissing the provider no longer has the show; stored data was kept
 * @param seasonsFetched seasons whose episode lists were fetched
 * @param episodesAdded new episode rows
 * @param episodesUpdated existing rows refreshed (including renumbered ones)
 * @param episodesMissing rows newly marked missing upstream
 */
public record SyncOutcome(long showId, boolean created, boolean upstreamMissing, int seasonsFetched,
		int episodesAdded, int episodesUpdated, int episodesMissing) {

	static SyncOutcome missing(long showId) {
		return new SyncOutcome(showId, false, true, 0, 0, 0, 0);
	}

}
