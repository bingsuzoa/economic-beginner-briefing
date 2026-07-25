-- 기사 원문 영구 저장
CREATE TABLE IF NOT EXISTS articles (
  id VARCHAR(64) PRIMARY KEY,
  source VARCHAR(128) NOT NULL,
  source_article_id VARCHAR(256),
  title TEXT NOT NULL,
  body TEXT,
  summary TEXT,
  url TEXT NOT NULL,
  author VARCHAR(256),
  category VARCHAR(64),
  published_at TIMESTAMPTZ,
  collected_at TIMESTAMPTZ NOT NULL,
  content_hash VARCHAR(128),
  language VARCHAR(10) DEFAULT 'ko',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(source, source_article_id),
  UNIQUE(content_hash)
);

CREATE INDEX idx_articles_published_at ON articles (published_at DESC);
CREATE INDEX idx_articles_source ON articles (source);

-- Teacher Label
CREATE TABLE IF NOT EXISTS teacher_labels (
  id SERIAL PRIMARY KEY,
  article_id VARCHAR(64) NOT NULL REFERENCES articles(id),
  label VARCHAR(20) NOT NULL,
  confidence DOUBLE PRECISION,
  reason TEXT,
  affected_areas JSONB,
  severity VARCHAR(20),
  needs_follow_up BOOLEAN DEFAULT FALSE,
  usable_for_training BOOLEAN DEFAULT TRUE,
  review_status VARCHAR(20) DEFAULT 'NONE',
  reviewed_by VARCHAR(128),
  reviewed_at TIMESTAMPTZ,
  teacher_model_provider VARCHAR(64),
  teacher_model_name VARCHAR(128),
  teacher_prompt_version VARCHAR(32),
  teacher_temperature DOUBLE PRECISION,
  labeled_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_teacher_labels_article_id ON teacher_labels (article_id);
CREATE INDEX idx_teacher_labels_label ON teacher_labels (label);
CREATE INDEX idx_teacher_labels_prompt_version ON teacher_labels (teacher_prompt_version);

-- pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Embedding (pgvector native vector type)
CREATE TABLE IF NOT EXISTS article_embeddings (
  id SERIAL PRIMARY KEY,
  article_id VARCHAR(64) NOT NULL REFERENCES articles(id),
  embedding_vector vector(1536) NOT NULL,
  embedding_model VARCHAR(128) NOT NULL,
  embedding_model_version VARCHAR(64),
  dimensions INTEGER NOT NULL,
  input_format VARCHAR(32) DEFAULT 'title_body',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(article_id, embedding_model)
);

CREATE INDEX idx_article_embeddings_article_id ON article_embeddings (article_id);
