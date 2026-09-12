#!/usr/bin/env python3
import argparse
import json
import os
import sys
from pathlib import Path

import psycopg
from openai import OpenAI

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))
from retrieval_pipeline import search


def load_env():
    path = ROOT / ".env"
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if line and not line.lstrip().startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                os.environ.setdefault(key.strip(), value.strip())


def main():
    parser = argparse.ArgumentParser(description="Economic Principle Vector Search")
    parser.add_argument("query")
    parser.add_argument("--top-k", type=int, default=5)
    args = parser.parse_args()
    load_env()
    model = os.getenv("EMBEDDING_MODEL", "text-embedding-3-large")
    dimensions = int(os.getenv("EMBEDDING_DIMENSIONS", "1536"))
    results, latency = search(args.query, args.top_k, model, dimensions, OpenAI(), psycopg.connect)
    print(json.dumps({"query": args.query, "model": model, "latency_ms": round(latency, 1),
                      "results": results}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
