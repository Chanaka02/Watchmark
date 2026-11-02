-- Seasons. season_number 0 holds specials.
--
-- A season that disappears upstream is kept with upstream_missing_at set.
-- MySQL has no partial unique indexes, so uniqueness applies to a generated
-- column that is NULL for missing rows: any number of missing seasons may
-- share a number, but only one active season per number exists.
--
-- metadata_synced_at: when this season's episodes were last fetched (NULL =
-- never; the season is known only from the show summary).
CREATE TABLE seasons (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    show_id              BIGINT       NOT NULL,
    season_number        INT          NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    air_date             DATE         NULL,
    poster_url           VARCHAR(512) NULL,
    episode_count        INT          NULL,
    metadata_synced_at   DATETIME(6)  NULL,
    upstream_missing_at  DATETIME(6)  NULL,
    active_season_number INT GENERATED ALWAYS AS (IF(upstream_missing_at IS NULL, season_number, NULL)) STORED,
    CONSTRAINT pk_seasons PRIMARY KEY (id),
    CONSTRAINT fk_seasons_show FOREIGN KEY (show_id) REFERENCES shows (id) ON DELETE CASCADE,
    CONSTRAINT uq_seasons_active UNIQUE (show_id, active_season_number),
    CONSTRAINT ck_seasons_number CHECK (season_number >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
