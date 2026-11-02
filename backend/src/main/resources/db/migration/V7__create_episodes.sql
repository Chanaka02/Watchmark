-- Episodes. season_number is denormalised from seasons for the uniqueness rule
-- and for calendar/progress queries; the application keeps both in step.
--
-- Rows are never deleted by a metadata refresh: an episode the provider drops
-- gets upstream_missing_at (rule R8) so watch history that references it stays
-- valid. As with seasons, uniqueness of (season, episode number) applies only
-- to active rows via a generated column.
--
-- external_id is the provider's own episode id; it identifies an episode even
-- when the provider renumbers it.
CREATE TABLE episodes (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    show_id               BIGINT       NOT NULL,
    season_id             BIGINT       NOT NULL,
    season_number         INT          NOT NULL,
    episode_number        INT          NOT NULL,
    external_id           VARCHAR(32)  NULL,
    name                  VARCHAR(255) NOT NULL,
    overview              TEXT         NULL,
    air_date              DATE         NULL,
    runtime_min           INT          NULL,
    still_url             VARCHAR(512) NULL,
    upstream_missing_at   DATETIME(6)  NULL,
    active_episode_number INT GENERATED ALWAYS AS (IF(upstream_missing_at IS NULL, episode_number, NULL)) STORED,
    CONSTRAINT pk_episodes PRIMARY KEY (id),
    CONSTRAINT fk_episodes_show FOREIGN KEY (show_id) REFERENCES shows (id) ON DELETE CASCADE,
    CONSTRAINT fk_episodes_season FOREIGN KEY (season_id) REFERENCES seasons (id) ON DELETE CASCADE,
    CONSTRAINT uq_episodes_active UNIQUE (show_id, season_number, active_episode_number),
    CONSTRAINT uq_episodes_external UNIQUE (show_id, external_id),
    CONSTRAINT ck_episodes_numbers CHECK (season_number >= 0 AND episode_number >= 0),
    CONSTRAINT ck_episodes_runtime CHECK (runtime_min IS NULL OR runtime_min > 0),
    INDEX ix_episodes_air_date (air_date),
    INDEX ix_episodes_season (season_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
