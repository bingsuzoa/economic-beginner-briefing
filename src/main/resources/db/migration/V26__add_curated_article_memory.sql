ALTER TABLE article_observations
  ADD COLUMN memory_eligible BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN source_snapshot JSONB;

ALTER TABLE article_observations ADD CONSTRAINT article_memory_has_source
  CHECK (NOT memory_eligible OR (source_snapshot IS NOT NULL
    AND source_snapshot ? 'bodyHash' AND source_snapshot ? 'publishedAt'
    AND source_snapshot ? 'spans'));
