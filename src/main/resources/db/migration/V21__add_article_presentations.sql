CREATE TABLE article_presentations (
  id BIGSERIAL PRIMARY KEY,
  article_id VARCHAR(64) NOT NULL,
  briefing_id VARCHAR(128) NOT NULL,
  presentation_json TEXT NOT NULL,
  model_name VARCHAR(128),
  presenter_prompt_version VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_article_presentations_article_created ON article_presentations(article_id, created_at DESC);
