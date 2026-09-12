from types import SimpleNamespace
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).parents[1] / "src"))
import retrieval_pipeline as rp


def chunk(chunk_id="c1", text="본문"):
    return {"chunk_id": chunk_id, "section_title": "소제목", "text": text}


class Embeddings:
    def __init__(self):
        self.calls = 0

    def create(self, input, **_):
        self.calls += 1
        return SimpleNamespace(data=[SimpleNamespace(index=i, embedding=[float(i), 1.0])
                                     for i, _ in reversed(list(enumerate(input)))])


def test_embedding_input_batch_mapping_and_cache(tmp_path):
    api = Embeddings()
    client = SimpleNamespace(embeddings=api)
    chunks = [chunk("c1"), chunk("c2", "다른 본문")]
    cache = tmp_path / "cache.jsonl"

    result, report = rp.embed_chunks(chunks, cache, dimensions=2, batch_size=2, client=client)
    assert result[0]["embedding_text"] == "소제목\n\n본문"
    assert [x["embedding"] for x in result] == [[0.0, 1.0], [1.0, 1.0]]
    assert report["api_calls"] == 1

    result, report = rp.embed_chunks(chunks, cache, dimensions=2, client=client)
    assert api.calls == 1
    assert report["cache_hits"] == 2 and len(result) == 2
    assert rp.content_hash("a", "m", 2) != rp.content_hash("b", "m", 2)


@pytest.mark.parametrize("bad", ([float("nan"), 1], [float("inf"), 1]))
def test_embedding_validation(bad):
    with pytest.raises(ValueError):
        rp.validate_embedding(bad, 2)


def test_duplicate_chunk_id_rejected(tmp_path):
    with pytest.raises(ValueError, match="duplicate chunk_id"):
        rp.embed_chunks([chunk(), chunk()], tmp_path / "cache", dimensions=2,
                        client=SimpleNamespace())


def test_hit_rates():
    assert rp.hit_rates([{"expected_rank": 1}, {"expected_rank": 3}, {"expected_rank": None}]) == {
        "hit_at_1": 1 / 3, "hit_at_3": 2 / 3, "hit_at_5": 2 / 3}


def test_cosine_search_mapping(monkeypatch):
    monkeypatch.setattr("pgvector.psycopg.register_vector", lambda conn: None)

    class Conn:
        def __enter__(self): return self
        def __exit__(self, *_): pass
        def execute(self, sql, params):
            assert "<=>" in sql and "::vector" in sql and params[-1] == 5 and params[2] == "경제원리.pdf"
            return SimpleNamespace(fetchall=lambda: [("c1", "제목", "본문", "body", "semantic", 1, 2, .2, .8)])

    client = SimpleNamespace(embeddings=SimpleNamespace(create=lambda **_: SimpleNamespace(
        data=[SimpleNamespace(embedding=[0.0, 1.0])])))
    rows, _ = rp.search("질문", 5, "model", 2, client, lambda _: Conn())
    assert rows[0]["rank"] == 1 and rows[0]["similarity"] == .8 and rows[0]["chunk_method"] == "semantic"


def test_upsert_counts_and_stale_delete(monkeypatch):
    monkeypatch.setattr("pgvector.psycopg.register_vector", lambda conn: None)

    class Result:
        def __init__(self, rows=()): self.rows = rows
        def __iter__(self): return iter(self.rows)
        def fetchone(self): return self.rows[0]

    class Conn:
        def __init__(self): self.sql = []
        def __enter__(self): return self
        def __exit__(self, *_): pass
        def execute(self, sql, params=None):
            self.sql.append(sql)
            if sql.startswith("SELECT chunk_id"):
                return Result([("same", "same-hash"), ("changed", "old-hash"), ("stale", "old")])
            if sql.startswith("SELECT count(*)"):
                return Result([(1 if "<>" in sql else 3,)])
            return Result()

    conn = Conn()
    base = {"parent_section_id": "p", "source": "book.pdf", "chapter": "3", "section_title": "t",
            "page_start": 1, "page_end": 2, "chunk_index": 0, "token_count": 10, "chunk_method": "m",
            "content_type": "body", "text": "x", "embedding_text": "t\n\nx", "embedding_model": "model",
            "embedding_dimensions": 2, "embedding": [0.0, 1.0]}
    items = [{**base, "chunk_id": "same", "content_hash": "same-hash"},
             {**base, "chunk_id": "changed", "content_hash": "new-hash"},
             {**base, "chunk_id": "new", "content_hash": "newer-hash"}]
    counts = rp.load_database(items, "CREATE TABLE example()", lambda _: conn)
    assert counts == {"insert": 1, "update": 1, "unchanged": 1, "stale_delete": 1}
    assert any(sql.startswith("DELETE FROM") for sql in conn.sql)
