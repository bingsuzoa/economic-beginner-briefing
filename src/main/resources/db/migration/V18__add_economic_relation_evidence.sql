ALTER TABLE event_relations
  ADD COLUMN provenance VARCHAR(32) NOT NULL DEFAULT 'STATE_TRANSITION';

CREATE TABLE event_relation_evidence (
  id BIGSERIAL PRIMARY KEY,
  relation_id BIGINT NOT NULL REFERENCES event_relations(id) ON DELETE CASCADE,
  article_id VARCHAR(64) NOT NULL REFERENCES articles(id),
  evidence_text TEXT NOT NULL,
  evidence_hash VARCHAR(64) NOT NULL,
  evidence_type VARCHAR(32) NOT NULL,
  speaker VARCHAR(256),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (relation_id, article_id, evidence_hash)
);

CREATE INDEX idx_event_relations_to ON event_relations (to_event_id);
CREATE INDEX idx_event_relations_from ON event_relations (from_event_id);
