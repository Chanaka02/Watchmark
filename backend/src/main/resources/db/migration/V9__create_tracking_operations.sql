-- One row per tracking action (watch, unwatch, mark season, watch up to), so the
-- action can be undone within the undo window (rule R3). Milestone 5.
--
-- id: a UUID (time-ordered, version 7) so operation ids in undo URLs cannot be
--   enumerated; ownership is still checked on every request (R1).
-- show_id: the show the action was about, so purging a show's history (R11)
--   deletes exactly its operations. RESTRICT: metadata outlives user data.
-- undone_at: set once when the action is undone; an operation is undone at most once.
--
-- Purged by the retention job (R12) once older than the retention period and no
-- episode_watches row references it any more.
CREATE TABLE tracking_operations (
    id         BINARY(16)  NOT NULL,
    user_id    BIGINT      NOT NULL,
    show_id    BIGINT      NOT NULL,
    type       VARCHAR(10) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    undone_at  DATETIME(6) NULL,
    CONSTRAINT pk_tracking_operations PRIMARY KEY (id),
    CONSTRAINT fk_tracking_operations_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_tracking_operations_show FOREIGN KEY (show_id) REFERENCES shows (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tracking_operations_type CHECK (type IN ('WATCH', 'UNWATCH', 'SEASON', 'UP_TO')),
    INDEX ix_tracking_operations_user_show (user_id, show_id),
    INDEX ix_tracking_operations_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
