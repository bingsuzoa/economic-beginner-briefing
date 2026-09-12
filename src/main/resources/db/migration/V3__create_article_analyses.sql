CREATE TABLE IF NOT EXISTS article_analyses (
  id SERIAL PRIMARY KEY,
  article_id VARCHAR(64) NOT NULL REFERENCES articles(id),
  briefing_id VARCHAR(128),
  analysis_json JSONB NOT NULL,
  model_name VARCHAR(128),
  prompt_version VARCHAR(32),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_article_analyses_article_id ON article_analyses (article_id);
CREATE INDEX idx_article_analyses_briefing_id ON article_analyses (briefing_id);
