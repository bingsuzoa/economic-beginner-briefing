-- Local (email/password) auth: add columns to users, create verification tokens table.
-- OAuth users have provider != 'local' and password_hash = NULL.

ALTER TABLE users ADD COLUMN IF NOT EXISTS nickname VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(256);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_nickname ON users (nickname) WHERE nickname IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_local_email ON users (email) WHERE provider = 'local';

CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id          VARCHAR(64)   PRIMARY KEY,
    user_id     VARCHAR(64)   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(128)  NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ   NOT NULL,
    used        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
