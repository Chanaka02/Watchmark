package app.watchmark.catalog.store;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import app.watchmark.catalog.dto.CatalogDtos.SeasonSummary;

@Entity
@Table(name = "seasons")
public class Season {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "show_id", nullable = false)
	private Show show;

	@Column(name = "season_number", nullable = false)
	private int seasonNumber;

	@Column(nullable = false)
	private String name;

	@Column(name = "air_date")
	private LocalDate airDate;

	@Column(name = "poster_url", length = 512)
	private String posterUrl;

	@Column(name = "episode_count")
	private Integer episodeCount;

	/** When episodes were last fetched; {@code null} = never. */
	@Column(name = "metadata_synced_at")
	private Instant episodesSyncedAt;

	@Column(name = "upstream_missing_at")
	private Instant upstreamMissingAt;

	protected Season() {
	}

	Season(Show show, int seasonNumber) {
		this.show = show;
		this.seasonNumber = seasonNumber;
	}

	/** Updates summary fields and (re)activates the season. */
	void apply(SeasonSummary summary) {
		name = Text.limit(summary.name(), 255);
		airDate = summary.airDate();
		posterUrl = Text.limit(summary.posterUrl(), 512);
		episodeCount = summary.episodeCount();
		upstreamMissingAt = null;
	}

	void episodesSynced(Instant now) {
		episodesSyncedAt = now;
	}

	void markUpstreamMissing(Instant now) {
		if (upstreamMissingAt == null) {
			upstreamMissingAt = now;
		}
	}

	public boolean isActive() {
		return upstreamMissingAt == null;
	}

	public Long getId() {
		return id;
	}

	public int getSeasonNumber() {
		return seasonNumber;
	}

	public String getName() {
		return name;
	}

	public LocalDate getAirDate() {
		return airDate;
	}

	public Integer getEpisodeCount() {
		return episodeCount;
	}

	public Instant getEpisodesSyncedAt() {
		return episodesSyncedAt;
	}

	public Instant getUpstreamMissingAt() {
		return upstreamMissingAt;
	}

}
