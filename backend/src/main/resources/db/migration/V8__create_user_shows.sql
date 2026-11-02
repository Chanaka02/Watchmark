-- A user's library entry for one show (Milestone 4).
--
-- removed_at: set when the user removes the show from their library. The row,
--   its rating, notes and cycle are kept so re-adding restores everything and
--   viewing history stays intact (rule R10). Permanent deletion is a separate,
--   explicit action (rule R11).
-- current_cycle: 1 = first watch-through, 2+ = rewatches (used from Milestone 5).
-- version: optimistic lock, so concurrent edits fail instead of overwriting.
--
-- show_id is RESTRICT: stored metadata can never be deleted while a user
-- references it.
CREATE TABLE user_shows (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    show_id       BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL,
    rating        INT         NULL,
    notes         TEXT        NULL,
    current_cycle INT         NOT NULL DEFAULT 1,
    added_at      DATETIME(6) NOT NULL,
    removed_at    DATETIME(6) NULL,
    updated_at    DATETIME(6) NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_shows PRIMARY KEY (id),
    CONSTRAINT fk_user_shows_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_shows_show FOREIGN KEY (show_id) REFERENCES shows (id) ON DELETE RESTRICT,
    CONSTRAINT uq_user_shows_user_show UNIQUE (user_id, show_id),
    CONSTRAINT ck_user_shows_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 10),
    CONSTRAINT ck_user_shows_cycle CHECK (current_cycle >= 1),
    CONSTRAINT ck_user_shows_status CHECK (status IN ('PLAN_TO_WATCH', 'WATCHING', 'COMPLETED', 'ON_HOLD', 'DROPPED')),
    INDEX ix_user_shows_library (user_id, removed_at, status),
    INDEX ix_user_shows_show (show_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
