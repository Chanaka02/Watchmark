-- Genres by name (providers' genre ids are not needed for statistics), and the
-- show-genre association, which is replaced on every metadata refresh.
CREATE TABLE genres (
    id   BIGINT       NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COLLATE utf8mb4_0900_as_ci,
    CONSTRAINT pk_genres PRIMARY KEY (id),
    CONSTRAINT uq_genres_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE show_genres (
    show_id  BIGINT NOT NULL,
    genre_id BIGINT NOT NULL,
    CONSTRAINT pk_show_genres PRIMARY KEY (show_id, genre_id),
    CONSTRAINT fk_show_genres_show FOREIGN KEY (show_id) REFERENCES shows (id) ON DELETE CASCADE,
    CONSTRAINT fk_show_genres_genre FOREIGN KEY (genre_id) REFERENCES genres (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
