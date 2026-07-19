-- Email verification columns
-- Existing users are pre-verified so they are not locked out after upgrade
ALTER TABLE users
    ADD COLUMN email_verified           BOOLEAN      NOT NULL DEFAULT FALSE AFTER email,
    ADD COLUMN email_verification_token VARCHAR(64)  NULL     AFTER email_verified,
    ADD COLUMN email_verification_exp   DATETIME     NULL     AFTER email_verification_token;

UPDATE users SET email_verified = TRUE WHERE email_verified = FALSE;

-- Account lockout columns
ALTER TABLE users
    ADD COLUMN failed_login_attempts    INT          NOT NULL DEFAULT 0    AFTER last_login_at,
    ADD COLUMN locked_until             DATETIME     NULL                  AFTER failed_login_attempts;

-- Index for token lookups
CREATE INDEX idx_users_verification_token ON users (email_verification_token);
