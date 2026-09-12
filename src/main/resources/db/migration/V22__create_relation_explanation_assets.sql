CREATE TABLE relation_explanation_assets (
  id BIGSERIAL PRIMARY KEY,
  relation_key VARCHAR(512) NOT NULL,
  relation_from TEXT NOT NULL,
  relation_to TEXT NOT NULL,
  relation_type VARCHAR(64) NOT NULL,
  explanation TEXT NOT NULL,
  explanation_kind VARCHAR(32) NOT NULL,
  source_article_id VARCHAR(64) NOT NULL,
  source_reference VARCHAR(256) NOT NULL,
  source_evidence TEXT,
  principle_chunk_ids TEXT,
  model_name VARCHAR(128),
  presenter_prompt_version VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_relation_explanation_assets_lookup
  ON relation_explanation_assets(relation_key, explanation_kind, created_at DESC);
