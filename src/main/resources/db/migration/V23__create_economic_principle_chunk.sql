CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE economic_principle_chunk (
    id BIGSERIAL PRIMARY KEY,
    chunk_id TEXT NOT NULL UNIQUE,
    parent_section_id TEXT NOT NULL,
    source TEXT NOT NULL,
    source_hash TEXT NOT NULL,
    chapter TEXT NOT NULL,
    section_title TEXT NOT NULL,
    page_start INTEGER NOT NULL,
    page_end INTEGER NOT NULL,
    chunk_index INTEGER NOT NULL,
    token_count INTEGER NOT NULL,
    chunk_method TEXT NOT NULL,
    content_type TEXT NOT NULL,
    text TEXT NOT NULL,
    embedding_text TEXT NOT NULL,
    embedding_model TEXT NOT NULL,
    embedding_dimensions INTEGER NOT NULL CHECK (embedding_dimensions = 1536),
    embedding vector(1536) NOT NULL,
    content_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX economic_principle_chunk_source_idx
    ON economic_principle_chunk (source, source_hash);
