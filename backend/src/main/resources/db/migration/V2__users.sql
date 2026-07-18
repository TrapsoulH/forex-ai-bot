-- ── V2: User accounts for Harvest Technologies platform ──────────────────────
-- Passwords are BCrypt-encoded. Default admin is seeded by DataInitializer.java
-- at startup (never hardcoded here to avoid plain-text secrets in migrations).

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)     NOT NULL UNIQUE,
    email         VARCHAR(120)    NOT NULL UNIQUE,
    password_hash VARCHAR(255)    NOT NULL,
    role          VARCHAR(20)     NOT NULL DEFAULT 'USER',   -- ADMIN | USER
    enabled       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_login_at DATETIME(3)     NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
