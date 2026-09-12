CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE articles
  ADD COLUMN IF NOT EXISTS body_status VARCHAR(16) NOT NULL DEFAULT 'RSS_ONLY',
  ADD COLUMN IF NOT EXISTS body_fetched_at TIMESTAMPTZ;

UPDATE articles
SET body_status = 'FULL_TEXT'
WHERE body IS NOT NULL AND btrim(body) <> '';

ALTER TABLE articles ALTER COLUMN summary SET DEFAULT '';

CREATE UNIQUE INDEX IF NOT EXISTS articles_url_unique_idx ON articles (url);
CREATE INDEX IF NOT EXISTS articles_published_at_idx ON articles (published_at DESC);

ALTER TABLE articles DROP CONSTRAINT IF EXISTS articles_body_status_check;
ALTER TABLE articles ADD CONSTRAINT articles_body_status_check
  CHECK (body_status IN ('RSS_ONLY', 'FULL_TEXT', 'FETCH_FAILED'));

CREATE TABLE article_observations (
  id VARCHAR(96) PRIMARY KEY,
  article_id VARCHAR(64) NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
  observation_no SMALLINT NOT NULL,
  observation_text TEXT NOT NULL,
  source_span_ids TEXT[] NOT NULL,
  embedding vector(1536),
  embedding_model VARCHAR(64),
  model_name VARCHAR(64) NOT NULL,
  prompt_version VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (article_id, observation_no),
  CHECK (cardinality(source_span_ids) > 0)
);

CREATE INDEX article_observations_article_idx ON article_observations (article_id);

CREATE TABLE daily_briefings (
  id VARCHAR(64) PRIMARY KEY,
  target_date DATE NOT NULL,
  revision INTEGER NOT NULL,
  status VARCHAR(16) NOT NULL,
  trigger_type VARCHAR(16) NOT NULL,
  pipeline_version VARCHAR(32) NOT NULL,
  models_json JSONB NOT NULL,
  usage_json JSONB NOT NULL DEFAULT '{}',
  trace_json JSONB NOT NULL DEFAULT '{}',
  result_json JSONB,
  input_hash VARCHAR(64),
  error_message TEXT,
  started_at TIMESTAMPTZ NOT NULL,
  finished_at TIMESTAMPTZ,
  UNIQUE (target_date, revision),
  CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX daily_briefings_latest_success_idx
  ON daily_briefings (target_date DESC, revision DESC)
  WHERE status = 'SUCCESS';

DROP TABLE IF EXISTS article_reading_history;
DROP TABLE IF EXISTS article_presentations;
DROP TABLE IF EXISTS article_router_results;
DROP TABLE IF EXISTS article_analyzer_results;
DROP TABLE IF EXISTS article_analyses;
DROP TABLE IF EXISTS article_embeddings;
DROP TABLE IF EXISTS teacher_labels;
DROP TABLE IF EXISTS relation_explanation_assets;
DROP TABLE IF EXISTS event_relation_evidence;
DROP TABLE IF EXISTS event_relations;
DROP TABLE IF EXISTS event_evidence;
DROP TABLE IF EXISTS event_topics;
DROP TABLE IF EXISTS economic_events;
DROP TABLE IF EXISTS topic_candidates;
DROP TABLE IF EXISTS topics;
DROP TABLE IF EXISTS economic_slot_values;
DROP TABLE IF EXISTS economic_slots;
DROP TABLE IF EXISTS economic_principle_chunks;
DROP TABLE IF EXISTS pipeline_items;
DROP TABLE IF EXISTS pipeline_logs;
DROP TABLE IF EXISTS pipeline_runs;

ALTER TABLE articles DROP CONSTRAINT IF EXISTS articles_content_hash_key;
ALTER TABLE articles
  DROP COLUMN IF EXISTS author,
  DROP COLUMN IF EXISTS category,
  DROP COLUMN IF EXISTS language;
