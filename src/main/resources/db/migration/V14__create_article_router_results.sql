CREATE TABLE IF NOT EXISTS article_router_results (
  id BIGSERIAL PRIMARY KEY,
  article_id VARCHAR(64) NOT NULL REFERENCES articles(id),
  briefing_id VARCHAR(128) NOT NULL,
  router_json JSONB NOT NULL,
  model_name VARCHAR(128),
  router_prompt_version VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_article_router_results_article_id
  ON article_router_results (article_id, created_at DESC);

CREATE INDEX idx_article_router_results_briefing_id
  ON article_router_results (briefing_id);

CREATE INDEX idx_article_router_results_created_at
  ON article_router_results (created_at DESC);
