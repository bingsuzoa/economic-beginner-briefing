#!/usr/bin/env python3
import argparse
import json
import os
import sys
from pathlib import Path

import psycopg

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))
from retrieval_pipeline import db_status, load_database


def main():
    parser = argparse.ArgumentParser(description="Embedded Chunk pgvector Loader")
    parser.add_argument("embedded", type=Path)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    env = ROOT / ".env"
    if env.exists():
        for line in env.read_text(encoding="utf-8").splitlines():
            if line and not line.lstrip().startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                os.environ.setdefault(key.strip(), value.strip())
    status = db_status(psycopg.connect)
    if args.dry_run:
        print(json.dumps(status, ensure_ascii=False))
        return
    if not status["connected"]:
        raise SystemExit(f"DB connection failed: {status.get('error')}")
    items = [json.loads(line) for line in args.embedded.read_text(encoding="utf-8").splitlines() if line]
    dimensions = {x["embedding_dimensions"] for x in items}
    if dimensions != {1536}:
        raise SystemExit(f"migration vector dimension is 1536, input is {dimensions}")
    sql = (ROOT / "sql" / "001_economic_principle_chunk.sql").read_text(encoding="utf-8")
    counts = load_database(items, sql, psycopg.connect)
    print(json.dumps(counts, ensure_ascii=False))


if __name__ == "__main__":
    main()
