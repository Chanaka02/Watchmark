package app.watchmark.catalog.store;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import app.watchmark.catalog.ShowRef;
import app.watchmark.catalog.ShowStatus;
import app.watchmark.catalog.dto.CatalogDtos.ShowDetails;

/** Stored provider metadata for one show. Never holds user data. */
@Entity
@Table(name = "shows")
public class Show {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 16)
	private String provider;

	@Column(name = "external_id", nullable = false, length = 32)
	private String externalId;

	@Column(nullable = false)
	private String name;

	@Column(name = "original_name")
	private String originalName;

	@Column(columnDefinition = "text")
	private String overview;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ShowStatus status;

	@Column(name = "first_air_date")
	private LocalDate firstAirDate;

	@Column(name = "last_air_date")
	private LocalDate lastAirDate;

	@Column(name = "poster_url", length = 512)
	private String posterUrl;

	@Column(name = "backdrop_url", length = 512)
	private String backdropUrl;

	@Column(name = "default_runtime_min")
	private Integer defaultRuntimeMinutes;

	@Column(name = "metadata_synced_at", nullable = false)
	private Instant metadataSyncedAt;

	@Column(name = "last_refresh_attempt_at")
	private Instant lastRefreshAttemptAt;

	@Column(name = "refresh_failures", nullable = false)
	private int refreshFailures;

	@Column(name = "upstream_missing_at")
	private Instant upstreamMissingAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@ManyToMany
	@JoinTable(name = "show_genres", joinColumns = @JoinColumn(name = "show_id"),
			inverseJoinColumns = @JoinColumn(name = "genre_id"))
	private Set<Genre> genres = new HashSet<>();

	protected Show() {
	}

	static Show create(ShowRef ref, ShowDetails details, Instant now) {
		Show show = new Show();
		show.provider = ref.provider();
		show.externalId = ref.externalId();
		show.createdAt = now;
		show.apply(details, Set.of(), now);
		return show;
	}

	/** Overwrites every metadata field with fresh provider data. */
	void apply(ShowDetails details, Set<Genre> newGenres, Instant now) {
		name = Text.limit(details.name(), 255);
		originalName = Text.limit(details.originalName(), 255);
		overview = details.overview();
		status = details.status() == null ? ShowStatus.UNKNOWN : details.status();
		firstAirDate = details.firstAirDate();
		lastAirDate = details.lastAirDate();
		posterUrl = Text.limit(details.posterUrl(), 512);
		backdropUrl = Text.limit(details.backdropUrl(), 512);
		defaultRuntimeMinutes = details.episodeRunTimeMinutes();
		genres.clear();
		genres.addAll(newGenres);
		metadataSyncedAt = now;
		lastRefreshAttemptAt = now;
		refreshFailures = 0;
		upstreamMissingAt = null;
	}

	void recordRefreshFailure(Instant now) {
		lastRefreshAttemptAt = now;
		refreshFailures++;
	}

	/** The provider no longer has this show: keep the data, stop treating it as current. */
	void markUpstreamMissing(Instant now) {
		lastRefreshAttemptAt = now;
		if (upstreamMissingAt == null) {
			upstreamMissingAt = now;
		}
	}

	public Long getId() {
		return id;
	}

	public ShowRef ref() {
		return new ShowRef(provider, externalId);
	}

	public String getName() {
		return name;
	}

	public String getOriginalName() {
		return originalName;
	}

	public String getOverview() {
		return overview;
	}

	public ShowStatus getStatus() {
		return status;
	}

	public LocalDate getFirstAirDate() {
		return firstAirDate;
	}

	public LocalDate getLastAirDate() {
		return lastAirDate;
	}

	public String getPosterUrl() {
		return posterUrl;
	}

	public String getBackdropUrl() {
		return backdropUrl;
	}

	public Integer getDefaultRuntimeMinutes() {
		return defaultRuntimeMinutes;
	}

	public Instant getMetadataSyncedAt() {
		return metadataSyncedAt;
	}

	public Instant getLastRefreshAttemptAt() {
		return lastRefreshAttemptAt;
	}

	public int getRefreshFailures() {
		return refreshFailures;
	}

	public Instant getUpstreamMissingAt() {
		return upstreamMissingAt;
	}

	public Set<Genre> getGenres() {
		return Set.copyOf(genres);
	}

}
