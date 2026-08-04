-- 기존 사용자는 보존하고 신규 계정 인증 컬럼만 추가한다.
ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(30);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_encrypted TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_hash VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(256);
ALTER TABLE users ALTER COLUMN phone DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_username ON users (username) WHERE username IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_hash ON users (email_hash) WHERE email_hash IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_nickname ON users (nickname) WHERE nickname IS NOT NULL;

-- 이메일 발송 연동 시 이 테이블의 원문 토큰 대신 token_hash만 저장한다.
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_password_reset_user ON password_reset_tokens (user_id, created_at DESC);

DROP TABLE IF EXISTS sms_verification_codes;
