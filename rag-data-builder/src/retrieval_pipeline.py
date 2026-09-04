import hashlib
import json
import math
import os
import time
from pathlib import Path

from openai import OpenAI

DEFAULT_MODEL = "text-embedding-3-large"
DEFAULT_DIMENSIONS = 1536
DEFAULT_BATCH_SIZE = 32


def embedding_text(chunk, mode="title_text"):
    if mode == "text":
        return chunk["text"]
    if mode == "chapter_title_text":
        return f"{chunk['chapter']}\n\n{chunk['section_title']}\n\n{chunk['text']}"
    return f"{chunk['section_title']}\n\n{chunk['text']}"


def content_hash(text, model, dimensions):
    return hashlib.sha256(f"{model}\0{dimensions}\0{text}".encode()).hexdigest()


def validate_embedding(vector, dimensions):
    if len(vector) != dimensions or not vector or not all(math.isfinite(x) for x in vector):
        raise ValueError(f"invalid embedding: expected {dimensions}, got {len(vector)} finite values")


def read_cache(path):
    if not path.exists():
        return {}
    cache = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if line:
            item = json.loads(line)
            cache[item["content_hash"]] = item
    return cache


def write_jsonl(path, items):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(x, ensure_ascii=False) + "\n" for x in items), encoding="utf-8")


def embed_chunks(chunks, cache_path, model=DEFAULT_MODEL, dimensions=DEFAULT_DIMENSIONS,
                 batch_size=DEFAULT_BATCH_SIZE, client=None, max_retries=3, dry_run=False):
    ids = [chunk["chunk_id"] for chunk in chunks]
    if len(ids) != len(set(ids)):
        raise ValueError("duplicate chunk_id")
    client = client or OpenAI()
    cache = read_cache(cache_path)
    prepared = []
    for chunk in chunks:
        text = embedding_text(chunk)
        if not text.strip():
            raise ValueError(f"empty embedding input: {chunk['chunk_id']}")
        prepared.append((chunk, text, content_hash(text, model, dimensions)))
    hits = sum(key in cache for _, _, key in prepared)
    missing = [(chunk, text, key) for chunk, text, key in prepared if key not in cache]
    if dry_run:
        return [], {"model": model, "dimensions": dimensions, "target_chunks": len(chunks),
                    "cache_hits": hits, "embedding_targets": len(missing),
                    "batch_count": math.ceil(len(missing) / batch_size), "api_calls": 0,
                    "token_usage": 0, "retries": 0, "failures": 0}
    api_calls = retries = token_usage = 0
    errors = []
    for offset in range(0, len(missing), batch_size):
        batch = missing[offset:offset + batch_size]
        for attempt in range(max_retries + 1):
            try:
                api_calls += 1
                response = client.embeddings.create(model=model, dimensions=dimensions,
                                                    input=[text for _, text, _ in batch], timeout=60)
                token_usage += getattr(getattr(response, "usage", None), "total_tokens", 0) or 0
                ordered = sorted(response.data, key=lambda x: x.index)
                if len(ordered) != len(batch):
                    raise ValueError("embedding batch count mismatch")
                for (chunk, text, key), result in zip(batch, ordered):
                    validate_embedding(result.embedding, dimensions)
                    cache[key] = {"content_hash": key, "chunk_id": chunk["chunk_id"],
                                  "model": model, "dimensions": dimensions,
                                  "embedding_text": text, "embedding": result.embedding}
                # Persist each completed batch so a later retry resumes without paying twice.
                write_jsonl(cache_path, cache.values())
                break
            except Exception as exc:
                if attempt == max_retries:
                    errors.extend({"chunk_id": chunk["chunk_id"], "error": str(exc)} for chunk, _, _ in batch)
                    break
                retries += 1
                time.sleep(min(2 ** attempt, 8))
    if errors:
        return [], {"model": model, "dimensions": dimensions, "target_chunks": len(chunks),
                    "cache_hits": hits, "embedding_targets": len(missing), "batch_count": math.ceil(len(missing) / batch_size),
                    "api_calls": api_calls, "token_usage": token_usage, "retries": retries,
                    "failures": len(errors), "errors": errors}
    write_jsonl(cache_path, cache.values())
    embedded = []
    for chunk, text, key in prepared:
        item = {**chunk, "embedding_text": text, "embedding_model": model,
                "embedding_dimensions": dimensions, "content_hash": key,
                "embedding": cache[key]["embedding"]}
        embedded.append(item)
    return embedded, {"model": model, "dimensions": dimensions, "target_chunks": len(chunks),
                      "cache_hits": hits, "embedding_targets": len(missing),
                      "batch_count": math.ceil(len(missing) / batch_size), "api_calls": api_calls,
                      "token_usage": token_usage, "retries": retries, "failures": 0}


def database_url():
    if url := os.getenv("DATABASE_URL"):
        return url
    host, port = os.getenv("DB_HOST", "localhost"), os.getenv("DB_PORT", "5432")
    name, user, password = os.getenv("DB_NAME", "economic_briefing"), os.getenv("DB_USER", os.getenv("USER", "")), os.getenv("DB_PASSWORD", "")
    auth = user + (f":{password}" if password else "")
    return f"postgresql://{auth}@{host}:{port}/{name}"


def db_status(connect):
    try:
        with connect(database_url()) as conn:
            row = conn.execute("""SELECT current_database(), version(),
                EXISTS (SELECT 1 FROM pg_extension WHERE extname='vector'),
                to_regclass('public.economic_principle_chunk') IS NOT NULL""").fetchone()
            return {"connected": True, "database": row[0], "version": row[1],
                    "pgvector": row[2], "table_exists": row[3]}
    except Exception as exc:
        return {"connected": False, "pgvector": False, "error": str(exc)}


def source_hash(items):
    return hashlib.sha256("\n".join(x["content_hash"] for x in items).encode()).hexdigest()


def load_database(items, migration_sql, connect):
    from pgvector.psycopg import register_vector

    build_hash = source_hash(items)
    source = items[0]["source"]
    with connect(database_url()) as conn:
        try:
            conn.execute("CREATE EXTENSION IF NOT EXISTS vector")
        except Exception as exc:
            raise RuntimeError("pgvector extension 생성 권한이 필요합니다. DBA가 CREATE EXTENSION vector를 실행해야 합니다.") from exc
        register_vector(conn)
        conn.execute(migration_sql)
        existing = {row[0]: row[1] for row in conn.execute(
            "SELECT chunk_id, content_hash FROM economic_principle_chunk WHERE source=%s", (source,))}
        counts = {"insert": 0, "update": 0, "unchanged": 0, "stale_delete": 0}
        sql = """INSERT INTO economic_principle_chunk
            (chunk_id,parent_section_id,source,source_hash,chapter,section_title,page_start,page_end,chunk_index,
             token_count,chunk_method,content_type,text,embedding_text,embedding_model,embedding_dimensions,embedding,content_hash)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
            ON CONFLICT (chunk_id) DO UPDATE SET
             parent_section_id=EXCLUDED.parent_section_id,source_hash=EXCLUDED.source_hash,chapter=EXCLUDED.chapter,
             section_title=EXCLUDED.section_title,page_start=EXCLUDED.page_start,page_end=EXCLUDED.page_end,
             chunk_index=EXCLUDED.chunk_index,token_count=EXCLUDED.token_count,chunk_method=EXCLUDED.chunk_method,
             content_type=EXCLUDED.content_type,text=EXCLUDED.text,embedding_text=EXCLUDED.embedding_text,
             embedding_model=EXCLUDED.embedding_model,embedding_dimensions=EXCLUDED.embedding_dimensions,
             embedding=EXCLUDED.embedding,content_hash=EXCLUDED.content_hash,updated_at=now()"""
        for item in items:
            state = "unchanged" if existing.get(item["chunk_id"]) == item["content_hash"] else ("update" if item["chunk_id"] in existing else "insert")
            counts[state] += 1
            if state == "unchanged":
                conn.execute("UPDATE economic_principle_chunk SET source_hash=%s, updated_at=now() WHERE chunk_id=%s", (build_hash, item["chunk_id"]))
                continue
            conn.execute(sql, (item["chunk_id"], item["parent_section_id"], source, build_hash, item["chapter"],
                              item["section_title"], item["page_start"], item["page_end"], item["chunk_index"],
                              item["token_count"], item["chunk_method"], item["content_type"], item["text"],
                              item["embedding_text"], item["embedding_model"], item["embedding_dimensions"],
                              item["embedding"], item["content_hash"]))
        stale = conn.execute("SELECT count(*) FROM economic_principle_chunk WHERE source=%s AND source_hash<>%s", (source, build_hash)).fetchone()[0]
        counts["stale_delete"] = stale
        print(f"stale rows to delete: {stale}")
        conn.execute("DELETE FROM economic_principle_chunk WHERE source=%s AND source_hash<>%s", (source, build_hash))
        loaded = conn.execute("SELECT count(*) FROM economic_principle_chunk WHERE source=%s AND source_hash=%s", (source, build_hash)).fetchone()[0]
        if loaded != len(items):
            raise ValueError(f"DB validation failed: expected {len(items)}, got {loaded}")
    return counts


SEARCH_SQL = """SELECT chunk_id, section_title, text, content_type, chunk_method, page_start, page_end,
                       embedding <=> %s::vector AS distance, 1 - (embedding <=> %s::vector) AS similarity
                FROM economic_principle_chunk
                WHERE source=%s AND embedding_model=%s AND embedding_dimensions=%s
                ORDER BY embedding <=> %s::vector LIMIT %s"""


def hit_rates(rows):
    return {f"hit_at_{k}": sum(x["expected_rank"] is not None and x["expected_rank"] <= k for x in rows) / len(rows)
            for k in (1, 3, 5)}


def search(query, top_k, model, dimensions, client, connect, source="경제원리.pdf"):
    from pgvector.psycopg import register_vector

    vector = client.embeddings.create(model=model, dimensions=dimensions, input=[query], timeout=60).data[0].embedding
    validate_embedding(vector, dimensions)
    started = time.perf_counter()
    with connect(database_url()) as conn:
        register_vector(conn)
        rows = conn.execute(SEARCH_SQL, (vector, vector, source, model, dimensions, vector, top_k)).fetchall()
    return [{"rank": i, "chunk_id": row[0], "section_title": row[1], "text": row[2],
             "content_type": row[3], "chunk_method": row[4], "page_start": row[5], "page_end": row[6],
             "distance": row[7], "similarity": row[8]} for i, row in enumerate(rows, 1)], (time.perf_counter() - started) * 1000
