CREATE TABLE IF NOT EXISTS article_analyzer_results (
  id SERIAL PRIMARY KEY,
  article_id VARCHAR(64) NOT NULL REFERENCES articles(id),
  briefing_id VARCHAR(128),
  analysis_json JSONB NOT NULL,
  model_name VARCHAR(128),
  analyzer_prompt_version VARCHAR(32),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_article_analyzer_results_article_id ON article_analyzer_results (article_id);
CREATE INDEX idx_article_analyzer_results_briefing_id ON article_analyzer_results (briefing_id);
CREATE INDEX idx_article_analyzer_results_created_at ON article_analyzer_results (created_at DESC);
