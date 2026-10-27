-- Accounts. Email is lowercased by the application before it is stored; the
-- accent-sensitive, case-insensitive collation makes the unique key reject
-- case variants while still treating "jose" and "josé" as different addresses.
CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(254) NOT NULL COLLATE utf8mb4_0900_as_ci,
    display_name  VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    time_zone     VARCHAR(64)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
