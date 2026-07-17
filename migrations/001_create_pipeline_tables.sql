-- Pipeline runs: one row per pipeline execution
CREATE TABLE IF NOT EXISTS pipeline_runs (
  id VARCHAR(64) PRIMARY KEY,
  status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
  trigger_type VARCHAR(20) NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  finished_at TIMESTAMPTZ,
  duration_ms INTEGER,
  current_step VARCHAR(20) NOT NULL DEFAULT 'INIT',
  collected_count INTEGER NOT NULL DEFAULT 0,
  duplicate_count INTEGER NOT NULL DEFAULT 0,
  analysis_success_count INTEGER NOT NULL DEFAULT 0,
  analysis_failure_count INTEGER NOT NULL DEFAULT 0,
  publish_success_count INTEGER NOT NULL DEFAULT 0,
  publish_failure_count INTEGER NOT NULL DEFAULT 0,
  total_failure_count INTEGER NOT NULL DEFAULT 0,
  error_code VARCHAR(64),
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pipeline_runs_started_at ON pipeline_runs (started_at DESC);
CREATE INDEX IF NOT EXISTS idx_pipeline_runs_status ON pipeline_runs (status);

-- Pipeline logs: structured operational logs per run
CREATE TABLE IF NOT EXISTS pipeline_logs (
  id SERIAL PRIMARY KEY,
  run_id VARCHAR(64) NOT NULL REFERENCES pipeline_runs(id) ON DELETE CASCADE,
  level VARCHAR(10) NOT NULL,
  step VARCHAR(20) NOT NULL,
  event_code VARCHAR(64),
  message TEXT NOT NULL,
  metadata_json JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pipeline_logs_run_id ON pipeline_logs (run_id);
CREATE INDEX IF NOT EXISTS idx_pipeline_logs_created_at ON pipeline_logs (created_at DESC);

-- Pipeline items: per-news processing results
CREATE TABLE IF NOT EXISTS pipeline_items (
  id SERIAL PRIMARY KEY,
  run_id VARCHAR(64) NOT NULL REFERENCES pipeline_runs(id) ON DELETE CASCADE,
  article_url TEXT,
  normalized_url TEXT,
  source VARCHAR(128),
  original_title TEXT,
  original_summary TEXT,
  published_at TIMESTAMPTZ,
  category VARCHAR(64),
  duplicate_status VARCHAR(20) NOT NULL DEFAULT 'UNIQUE',
  analysis_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  analysis_result_json JSONB,
  analysis_error_message TEXT,
  publish_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  notion_page_id VARCHAR(128),
  notion_page_url TEXT,
  publish_error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pipeline_items_run_id ON pipeline_items (run_id);
CREATE INDEX IF NOT EXISTS idx_pipeline_items_normalized_url ON pipeline_items (normalized_url);
CREATE INDEX IF NOT EXISTS idx_pipeline_items_notion_page_id ON pipeline_items (notion_page_id);
