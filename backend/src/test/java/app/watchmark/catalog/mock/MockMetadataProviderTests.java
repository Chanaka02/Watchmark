package app.watchmark.catalog.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import app.watchmark.catalog.DiscoverList;
import app.watchmark.catalog.MetadataNotFoundException;
import app.watchmark.catalog.ShowStatus;
import app.watchmark.catalog.dto.CatalogDtos.EpisodeInfo;
import app.watchmark.catalog.dto.CatalogDtos.SeasonSummary;
import app.watchmark.catalog.dto.CatalogDtos.ShowSummary;

class MockMetadataProviderTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

	private final MockMetadataProvider provider = new MockMetadataProvider(
			Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC));

	@Test
	void listsArePaginated() {
		var first = provider.discover(DiscoverList.TRENDING, 1);
		var second = provider.discover(DiscoverList.TRENDING, 2);

		assertThat(first.items()).hasSize(MockMetadataProvider.PAGE_SIZE);
		assertThat(first.totalPages()).isEqualTo(2);
		assertThat(first.totalResults()).isEqualTo(8);
		assertThat(second.items()).hasSize(2);
		assertThat(provider.discover(DiscoverList.TRENDING, 3).items()).isEmpty();
	}

	@Test
	void onTheAirContainsOnlyShowsAiringThisWeek() {
		assertThat(provider.discover(DiscoverList.ON_THE_AIR, 1).items()).extracting(ShowSummary::ref)
			.containsExactly("mock:1001", "mock:1005");
	}

	@Test
	void topRatedExcludesUnratedShows() {
		assertThat(provider.discover(DiscoverList.TOP_RATED, 1).totalResults()).isEqualTo(6);
	}

	@Test
	void searchIsCaseInsensitive() {
		assertThat(provider.search("LIGHTS", 1).items()).extracting(ShowSummary::name)
			.containsExactly("Northern Lights Station", "Harbor Lights");
		assertThat(provider.search("no such show", 1).items()).isEmpty();
	}

	@Test
	void showCoversSpecialsTodayAndFutureEpisodes() {
		var show = provider.show("1001");

		assertThat(show.status()).isEqualTo(ShowStatus.RETURNING);
		assertThat(show.seasons()).extracting(SeasonSummary::seasonNumber).containsExactly(0, 1, 2, 3);
		assertThat(show.lastEpisode()).extracting(EpisodeInfo::seasonNumber, EpisodeInfo::episodeNumber,
				EpisodeInfo::airDate)
			.containsExactly(2, 4, TODAY);
		assertThat(show.nextEpisode()).extracting(EpisodeInfo::episodeNumber, EpisodeInfo::airDate)
			.containsExactly(5, TODAY.plusDays(7));
		assertThat(show.posterUrl()).as("mock data has no images").isNull();
	}

	@Test
	void incompleteMetadataIsRepresentedAsNull() {
		var specials = provider.season("1001", 0);
		assertThat(specials.episodes()).extracting(EpisodeInfo::airDate).containsExactly(TODAY.minusDays(400), null);

		var archive = provider.season("1002", 1);
		assertThat(archive.episodes()).filteredOn(episode -> episode.airDate() == null).hasSize(2);
		assertThat(archive.episodes()).allSatisfy(episode -> assertThat(episode.runtimeMinutes()).isNull());

		assertThat(provider.show("1003").overview()).isNull();
		assertThat(provider.show("1004").seasons()).isEmpty();
		assertThat(provider.season("1001", 3).episodes()).as("announced, no episodes").isEmpty();
	}

	@Test
	void episodeIdsAreStableAndUnique() {
		var ids = provider.season("1001", 2).episodes().stream().map(EpisodeInfo::externalId).toList();
		assertThat(ids).doesNotHaveDuplicates().containsExactly("10010201", "10010202", "10010203", "10010204",
				"10010205", "10010206");
	}

	@Test
	void unknownShowsAndSeasonsAreNotFound() {
		assertThatThrownBy(() -> provider.show("9999")).isInstanceOf(MetadataNotFoundException.class);
		assertThatThrownBy(() -> provider.season("1001", 9)).isInstanceOf(MetadataNotFoundException.class);
	}

}
