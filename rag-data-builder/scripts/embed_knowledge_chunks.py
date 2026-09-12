#!/usr/bin/env python3
import argparse
import json
import os
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))
from retrieval_pipeline import embed_chunks, write_jsonl


def load_env():
    path = ROOT / ".env"
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if line and not line.lstrip().startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                os.environ.setdefault(key.strip(), value.strip())


def main():
    parser = argparse.ArgumentParser(description="Final Chunk Embedding")
    parser.add_argument("chunks", type=Path)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "output" / "embeddings")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    load_env()
    model = os.getenv("EMBEDDING_MODEL", "text-embedding-3-large")
    dimensions = int(os.getenv("EMBEDDING_DIMENSIONS", "1536"))
    batch_size = int(os.getenv("EMBEDDING_BATCH_SIZE", "32"))
    chunks = json.loads(args.chunks.read_text(encoding="utf-8"))
    args.output_dir.mkdir(parents=True, exist_ok=True)
    started = time.perf_counter()
    embedded, report = embed_chunks(chunks, args.output_dir / "embeddings_cache.jsonl", model,
                                    dimensions, batch_size, dry_run=args.dry_run)
    report.update({"source_chunks_path": str(args.chunks.resolve()), "total_chunks": len(chunks),
                   "batch_size": batch_size, "cache_misses": report["embedding_targets"],
                   "successful_embeddings": len(embedded) if not args.dry_run else 0,
                   "failed_embeddings": report["failures"],
                   "elapsed_seconds": round(time.perf_counter() - started, 3)})
    errors = report.pop("errors", [])
    (args.output_dir / "embedding_report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    (args.output_dir / "embedding_errors.json").write_text(json.dumps(errors, ensure_ascii=False, indent=2), encoding="utf-8")
    if embedded:
        write_jsonl(args.output_dir / "embedded_chunks.jsonl", embedded)
    print(json.dumps(report, ensure_ascii=False))
    if errors:
        raise SystemExit("Embedding batch failed; see embedding_errors.json")


if __name__ == "__main__":
    main()
