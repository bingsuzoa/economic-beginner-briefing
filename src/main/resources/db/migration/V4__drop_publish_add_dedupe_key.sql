-- Notion publishing was removed: the briefing is served from article_analyses via the
-- public API, so there is no external publish channel left to track.
ALTER TABLE pipeline_runs
  DROP COLUMN IF EXISTS publish_success_count,
  DROP COLUMN IF EXISTS publish_failure_count;

ALTER TABLE pipeline_items
  DROP COLUMN IF EXISTS publish_status,
  DROP COLUMN IF EXISTS notion_page_id,
  DROP COLUMN IF EXISTS notion_page_url,
  DROP COLUMN IF EXISTS publish_error_message;

-- Duplicate-run detection keys on this, not on target_date: hourly runs need
-- date+hour granularity, and the value must survive a restart.
ALTER TABLE pipeline_runs ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_pipeline_runs_dedupe_key
  ON pipeline_runs (dedupe_key, started_at DESC);
