#!/usr/bin/env python3
import json
import os
import sys
from pathlib import Path

import psycopg
from openai import OpenAI

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))
from retrieval_pipeline import hit_rates, search

CASES = [
    ("왜 환율이 오르면 물가도 오르나요?", "환율 뛰면 왜 물가도 뛰나"),
    ("경기가 좋아지면 왜 물가가 상승하나요?", "경기 좋아지면 왜 물가 오르나"),
    ("국제유가가 떨어지면 물가는 왜 내려가나요?", "원유 값 떨어지면 왜 물가도 떨어지나"),
    ("소비가 늘어나면 왜 물가가 오를 수 있나요?", "가계 소비가 어떻게 물가를 움직이나"),
    ("중앙은행이 금리를 낮추면 어떻게 경기를 살리나요?", "중앙은행 금융정책이 어떻게 경기 살리나"),
    ("회사채와 국채의 금리 차이가 커지면 왜 위험한가요?", "회사채와 국채 금리 차 커지면 왜 채권이 위험해지나"),
    ("엔화 가치가 떨어지면 왜 한국 수출에 불리한가요?", "'엔 저'는 왜 우리 수출에 빨간불인가"),
    ("소비자물가지수와 GDP 디플레이터는 뭐가 다른가요?", "소비자물가지수와 GDP 디플레이터, 뭐가 다를까"),
    ("디플레이션은 왜 경제에 문제가 되나요?", "디플레이션은 뭐가 문제인가"),
    ("금값은 어떤 요인 때문에 움직이나요?", "금값은 어떻게 움직이나"),
    ("원화 가치가 떨어지면 국내 물가에는 어떤 일이 생기나요?", "환율 뛰면 왜 물가도 뛰나"),
    ("경제가 활황이면 상품 가격이 왜 비싸질 수 있죠?", "경기 좋아지면 왜 물가 오르나"),
    ("일본 엔화 약세가 한국 기업 수출 경쟁력을 왜 떨어뜨리나요?", "'엔 저'는 왜 우리 수출에 빨간불인가"),
    ("중앙은행의 금리 인하가 소비와 투자를 늘리는 과정이 궁금해요", "중앙은행 금융정책이 어떻게 경기 살리나"),
    ("기업 부도 위험이 커질 때 채권 금리 차이는 어떻게 변하나요?", "회사채와 국채 금리 차 커지면 왜 채권이 위험해지나"),
]


def main():
    for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        if line and not line.lstrip().startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            os.environ.setdefault(key.strip(), value.strip())
    model, dimensions = os.getenv("EMBEDDING_MODEL", "text-embedding-3-large"), int(os.getenv("EMBEDDING_DIMENSIONS", "1536"))
    client, rows = OpenAI(), []
    for query, expected in CASES:
        results, latency = search(query, 5, model, dimensions, client, psycopg.connect)
        ranks = [x["rank"] for x in results if expected in x["section_title"]]
        rows.append({"query": query, "embedding_model": model, "expected_section": expected,
                     "latency_ms": round(latency, 1), "results": results,
                     "expected_rank": ranks[0] if ranks else None})
    report = {"query_count": len(rows), "model": model, "dimensions": dimensions, "top_k": 5,
              **hit_rates(rows)}
    output = ROOT / "output" / "retrieval_test"
    output.mkdir(parents=True, exist_ok=True)
    (output / "retrieval_results.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = []
    for row in rows:
        lines.append(f"Query: {row['query']}\nExpected: {row['expected_section']}\nExpected Rank: {row['expected_rank']}")
        for item in row["results"]:
            lines.append(f"Rank {item['rank']} | {item['section_title']} | {item['chunk_id']}\n"
                         f"distance={item['distance']:.6f} similarity={item['similarity']:.6f} | {item['content_type']} | "
                         f"{item['chunk_method']} | page {item['page_start']}~{item['page_end']}\n{item['text'][:300]}")
    (output / "retrieval_results.txt").write_text("\n\n".join(lines), encoding="utf-8")
    (output / "retrieval_report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
