-- V8: 뉴스 읽음 이력 테이블 생성
CREATE TABLE IF NOT EXISTS article_reading_history (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    article_id VARCHAR(64) NOT NULL,
    read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_reading_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_reading_history_article FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_article_read UNIQUE (user_id, article_id)
);

CREATE INDEX idx_reading_history_user_read_at ON article_reading_history (user_id, read_at DESC);
CREATE INDEX idx_reading_history_article ON article_reading_history (article_id);
