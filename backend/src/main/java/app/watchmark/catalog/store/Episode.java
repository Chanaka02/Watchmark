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

import app.watchmark.catalog.dto.CatalogDtos.EpisodeInfo;

@Entity
@Table(name = "episodes")
public class Episode {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "show_id", nullable = false)
	private Show show;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "season_id", nullable = false)
	private Season season;

	@Column(name = "season_number", nullable = false)
	private int seasonNumber;

	@Column(name = "episode_number", nullable = false)
	private int episodeNumber;

	@Column(name = "external_id", length = 32)
	private String externalId;

	@Column(nullable = false)
	private String name;

	@Column(columnDefinition = "text")
	private String overview;

	@Column(name = "air_date")
	private LocalDate airDate;

	@Column(name = "runtime_min")
	private Integer runtimeMinutes;

	@Column(name = "still_url", length = 512)
	private String stillUrl;

	@Column(name = "upstream_missing_at")
	private Instant upstreamMissingAt;

	protected Episode() {
	}

	Episode(Show show, Season season, EpisodeInfo info) {
		this.show = show;
		apply(season, info);
	}

	/** Moves the episode to its current position and data, and (re)activates it. */
	void apply(Season newSeason, EpisodeInfo info) {
		season = newSeason;
		seasonNumber = newSeason.getSeasonNumber();
		episodeNumber = info.episodeNumber();
		externalId = Text.limit(info.externalId(), 32);
		name = Text.limit(info.name(), 255);
		overview = info.overview();
		airDate = info.airDate();
		runtimeMinutes = info.runtimeMinutes();
		stillUrl = Text.limit(info.stillUrl(), 512);
		upstreamMissingAt = null;
	}

	void markUpstreamMissing(Instant now) {
		if (upstreamMissingAt == null) {
			upstreamMissingAt = now;
		}
	}

	boolean occupies(int seasonNumber, int episodeNumber) {
		return this.seasonNumber == seasonNumber && this.episodeNumber == episodeNumber;
	}

	boolean belongsTo(Season candidate) {
		return season.getId().equals(candidate.getId());
	}

	public boolean isActive() {
		return upstreamMissingAt == null;
	}

	public Long getId() {
		return id;
	}

	public Long getSeasonId() {
		return season.getId();
	}

	public int getSeasonNumber() {
		return seasonNumber;
	}

	public int getEpisodeNumber() {
		return episodeNumber;
	}

	public String getExternalId() {
		return externalId;
	}

	public String getName() {
		return name;
	}

	public String getOverview() {
		return overview;
	}

	public LocalDate getAirDate() {
		return airDate;
	}

	public Integer getRuntimeMinutes() {
		return runtimeMinutes;
	}

	public Instant getUpstreamMissingAt() {
		return upstreamMissingAt;
	}

}
