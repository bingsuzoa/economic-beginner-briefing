-- SMS-only auth: drop OAuth/email columns and tables, add SMS verification.

DROP TABLE IF EXISTS email_verification_tokens;
DROP INDEX IF EXISTS uq_users_local_email;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_provider_provider_id_key;
ALTER TABLE users
    DROP COLUMN IF EXISTS provider,
    DROP COLUMN IF EXISTS provider_id,
    DROP COLUMN IF EXISTS email,
    DROP COLUMN IF EXISTS password_hash,
    DROP COLUMN IF EXISTS email_verified;
ALTER TABLE users ADD COLUMN IF NOT EXISTS birth_date DATE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS gender VARCHAR(10);
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN NOT NULL DEFAULT FALSE;
-- phone이 없는 기존 OAuth/이메일 사용자 제거 (파괴적 전환)
DELETE FROM users WHERE phone IS NULL OR phone = '';
ALTER TABLE users ALTER COLUMN phone SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_phone ON users (phone);

CREATE TABLE IF NOT EXISTS sms_verification_codes (
    id         BIGSERIAL    PRIMARY KEY,
    phone      VARCHAR(20)  NOT NULL,
    code_hash  VARCHAR(256) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    attempts   INT          NOT NULL DEFAULT 0,
    verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_sms_codes_phone ON sms_verification_codes (phone, created_at DESC);
