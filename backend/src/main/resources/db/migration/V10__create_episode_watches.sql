-- One row per viewing of an episode (rules R2–R4). Milestone 5.
--
-- cycle: the watch-through the viewing belongs to (user_shows.current_cycle at
--   the time); a rewatch is a new cycle, so the same episode can appear once per cycle.
-- deleted_at / deleted_by_op: unwatching (or undoing a watch) soft-deletes the
--   row so it can be restored by undo; the retention job (R12) deletes rows whose
--   deleted_at is older than the retention period.
-- active_marker: 1 while the row is active, NULL once soft-deleted. MySQL has no
--   partial indexes, and treats NULLs in a unique key as distinct, so the unique
--   key allows any number of soft-deleted rows but only one active row per user,
--   episode and cycle (R4).
--
-- There is no foreign key to user_shows: history belongs to the user and the
-- episode, and survives removing the show from the library (R10).
-- episode_id is RESTRICT: a metadata refresh never deletes episodes (R8), and
-- unreferenced-show cleanup checks this table first.
CREATE TABLE episode_watches (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    episode_id    BIGINT      NOT NULL,
    cycle         INT         NOT NULL,
    watched_at    DATETIME(6) NOT NULL,
    created_by_op BINARY(16)  NOT NULL,
    deleted_at    DATETIME(6) NULL,
    deleted_by_op BINARY(16)  NULL,
    active_marker TINYINT GENERATED ALWAYS AS (IF(deleted_at IS NULL, 1, NULL)) STORED,
    CONSTRAINT pk_episode_watches PRIMARY KEY (id),
    CONSTRAINT fk_episode_watches_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_episode_watches_episode FOREIGN KEY (episode_id) REFERENCES episodes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_episode_watches_created_by FOREIGN KEY (created_by_op) REFERENCES tracking_operations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_episode_watches_deleted_by FOREIGN KEY (deleted_by_op) REFERENCES tracking_operations (id) ON DELETE RESTRICT,
    CONSTRAINT uq_watch_active UNIQUE (user_id, episode_id, cycle, active_marker),
    CONSTRAINT ck_episode_watches_cycle CHECK (cycle >= 1),
    INDEX ix_episode_watches_user_watched (user_id, watched_at),
    INDEX ix_episode_watches_deleted (deleted_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
