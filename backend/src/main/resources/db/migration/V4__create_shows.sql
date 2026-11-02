-- Stored show metadata (Milestone 3). Only what tracking, the library, the
-- calendar and statistics need is stored; show pages still read the provider.
--
-- metadata_synced_at: when provider data was last written successfully.
--   TMDB forbids caching its content for more than six months; the refresh job
--   keeps every row far younger than that (see docs/tmdb.md).
-- upstream_missing_at: set when the provider no longer knows the show. The row
--   is kept so users' history stays intact (rule R8).
CREATE TABLE shows (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    provider                VARCHAR(16)  NOT NULL,
    external_id             VARCHAR(32)  NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    original_name           VARCHAR(255) NULL,
    overview                TEXT         NULL,
    status                  VARCHAR(20)  NOT NULL,
    first_air_date          DATE         NULL,
    last_air_date           DATE         NULL,
    poster_url              VARCHAR(512) NULL,
    backdrop_url            VARCHAR(512) NULL,
    default_runtime_min     INT          NULL,
    metadata_synced_at      DATETIME(6)  NOT NULL,
    last_refresh_attempt_at DATETIME(6)  NULL,
    refresh_failures        INT          NOT NULL DEFAULT 0,
    upstream_missing_at     DATETIME(6)  NULL,
    created_at              DATETIME(6)  NOT NULL,
    CONSTRAINT pk_shows PRIMARY KEY (id),
    CONSTRAINT uq_shows_provider_external UNIQUE (provider, external_id),
    CONSTRAINT ck_shows_runtime CHECK (default_runtime_min IS NULL OR default_runtime_min > 0),
    INDEX ix_shows_synced (metadata_synced_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
