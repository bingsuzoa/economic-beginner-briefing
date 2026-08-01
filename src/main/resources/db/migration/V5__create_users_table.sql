CREATE TABLE IF NOT EXISTS users (
    id              VARCHAR(64)   PRIMARY KEY,
    provider        VARCHAR(20)   NOT NULL,
    provider_id     VARCHAR(256)  NOT NULL,
    email           VARCHAR(256),
    name            VARCHAR(256),
    profile_image_url TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMPTZ,
    UNIQUE (provider, provider_id)
);
