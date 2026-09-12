#!/usr/bin/env python3
import argparse
import csv
import json
import logging
import os
import statistics
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from haystack import Document
from haystack.components.preprocessors import RecursiveDocumentSplitter
from langchain_text_splitters import RecursiveCharacterTextSplitter

from semantic_chunker import SYSTEM_PROMPT, semantic_boundaries_with_info
from token_utils import count_tokens

MODEL = "gpt-5-mini"
TARGET_TOKENS = 800
TARGET_TITLES = [
    "중동 유가는 왜 문제인가",
    "석유 정치경제학: ① 미국은 왜 중동에서 싸우나",
    "원유 값, 내리면 왜 문제인가",
    "환율 뛰면 왜 물가도 뛰나",
]


def load_env(path):
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if line and not line.lstrip().startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                os.environ.setdefault(key.strip(), value.strip())


def paragraph_spans(text):
    spans, cursor = [], 0
    for index, paragraph in enumerate(text.split("\n\n"), 1):
        start = text.find(paragraph, cursor)
        spans.append((start, start + len(paragraph), index))
        cursor = start + len(paragraph)
    return spans


def exact_chunks(text, split_texts):
    """Keep framework boundaries but slice the immutable parent text itself."""
    starts, cursor = [0], 0
    for split in split_texts[1:]:
        needle = split.lstrip()
        start = text.find(needle, cursor)
        if start < 0:
            raise ValueError("split boundary를 Parent Section에서 찾을 수 없습니다")
        starts.append(start)
        cursor = start + len(needle)
    return [text[start:(starts[i + 1] if i + 1 < len(starts) else len(text))]
            for i, start in enumerate(starts)]


def paragraph_range(chunk_start, chunk_end, spans):
    hit = [i for start, end, i in spans if start < chunk_end and end > chunk_start]
    return hit[0], hit[-1]


def records(parent, method, texts, paragraph_bounds=None):
    result, offset, spans = [], 0, paragraph_spans(parent["text"])
    parent_id = f"chapter3_section{parent['section_index']}"
    for index, text in enumerate(texts, 1):
        start, end = offset, offset + len(text)
        p_start, p_end = paragraph_bounds[index - 1] if paragraph_bounds else paragraph_range(start, end, spans)
        result.append({
            "parent_section_id": parent_id, "parent_title": parent["title"], "chapter": parent["chapter"],
            "chunk_index": index, "page_start": parent["page_start"], "page_end": parent["page_end"],
            "token_count": count_tokens(text, MODEL), "chunk_method": method,
            "start_paragraph": p_start, "end_paragraph": p_end, "text": text,
        })
        offset = end
    return result


def validation(parent_text, chunks):
    joined = "".join(x["text"] for x in chunks)
    normalized = "".join(joined.split()) == "".join(parent_text.split())
    return {"raw_text_equal": joined == parent_text, "normalized_text_equal": normalized,
            "no_missing_or_duplicate_text": joined == parent_text,
            "order_preserved": joined == parent_text, "parent_boundary_preserved": joined == parent_text,
            "result": "PASS" if joined == parent_text and normalized else "FAIL"}


def token_stats(chunks):
    values = [x["token_count"] for x in chunks]
    return {"min": min(values), "max": max(values), "avg": round(statistics.mean(values), 1),
            "median": statistics.median(values), "over_1200": sum(x >= 1200 for x in values)}


def boundary_text(chunks):
    return " / ".join(f"P{x['start_paragraph']}~P{x['end_paragraph']}" for x in chunks)


def method_preview(title, parent_tokens, chunks):
    lines = [f"{'=' * 62}\nPARENT SECTION\n{title}\nParent Tokens: {parent_tokens}\n{'=' * 62}"]
    for chunk in chunks:
        lines.append(f"CHUNK {chunk['chunk_index']}\nTokens: {chunk['token_count']}\n"
                     f"Paragraphs: P{chunk['start_paragraph']}~P{chunk['end_paragraph']}\n"
                     f"Text:\n{chunk['text']}\n")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Custom Parent Section 내부 Chunking 비교 PoC")
    parser.add_argument("--input", type=Path, default=ROOT / "output" / "poc" / "custom_sections.json")
    parser.add_argument("--section")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "output" / "internal_chunking_poc")
    args = parser.parse_args()
    load_env(ROOT / ".env")
    if not os.getenv("OPENAI_API_KEY"):
        parser.error("OPENAI_API_KEY가 없습니다")
    source = json.loads(args.input.read_text(encoding="utf-8"))
    wanted = [args.section] if args.section else TARGET_TITLES
    parents = [x for title in wanted for x in source if x["title"] == title]
    if len(parents) != len(wanted):
        parser.error("요청한 Parent Section을 모두 찾지 못했습니다")
    output = args.output_dir
    output.mkdir(parents=True, exist_ok=True)
    (output / "input_sections.json").write_text(json.dumps(parents, ensure_ascii=False, indent=2), encoding="utf-8")

    lc_splitter = RecursiveCharacterTextSplitter.from_tiktoken_encoder(
        encoding_name="o200k_base", chunk_size=TARGET_TOKENS, chunk_overlap=0,
        separators=["\n\n", "\n", "(?<=[.!?]) ", " ", ""],
        keep_separator=True, strip_whitespace=False,
    )
    hs_splitter = RecursiveDocumentSplitter(
        split_length=TARGET_TOKENS, split_overlap=0, split_unit="token",
        separators=["\n\n", "sentence", "\n", " "],
    )
    all_results = {"langchain": [], "haystack": [], "semantic": []}
    logs, reports = [], []
    for parent in parents:
        text, paragraphs = parent["text"], parent["text"].split("\n\n")
        lc_texts = exact_chunks(text, lc_splitter.split_text(text))
        hs_docs = hs_splitter.run(documents=[Document(content=text)])["documents"]
        hs_texts = exact_chunks(text, [x.content for x in hs_docs])
        boundaries, info = semantic_boundaries_with_info(paragraphs, MODEL)
        semantic_texts = ["\n\n".join(paragraphs[start - 1:end]) + ("\n\n" if i < len(boundaries) - 1 else "")
                          for i, (start, end) in enumerate(boundaries)]
        results = {
            "langchain": records(parent, "langchain", lc_texts),
            "haystack": records(parent, "haystack", hs_texts),
            "semantic": records(parent, "semantic", semantic_texts, boundaries),
        }
        for method, chunks in results.items():
            all_results[method].extend(chunks)
        checks = {method: validation(text, chunks) for method, chunks in results.items()}
        logs.append({"model": MODEL, "section_title": parent["title"],
                     "input_token_count": parent["token_count"], "paragraph_count": len(paragraphs),
                     "output_chunk_count": len(results["semantic"]), **info})
        reports.append({"parent_title": parent["title"], "parent_token_count": parent["token_count"],
                        "methods": {method: {"chunk_count": len(chunks),
                                             "tokens": [x["token_count"] for x in chunks],
                                             "boundaries": boundary_text(chunks),
                                             "token_distribution": token_stats(chunks),
                                             "validation": checks[method]}
                                    for method, chunks in results.items()},
                        "semantic_quality": "MANUAL_REVIEW_REQUIRED"})

    for method, chunks in all_results.items():
        (output / f"{method}_chunks.json").write_text(json.dumps(chunks, ensure_ascii=False, indent=2), encoding="utf-8")
        grouped = [method_preview(p["title"], p["token_count"], [x for x in chunks if x["parent_title"] == p["title"]]) for p in parents]
        (output / f"{method}_preview.txt").write_text("\n\n".join(grouped), encoding="utf-8")
    (output / "llm_calls.json").write_text(json.dumps(logs, ensure_ascii=False, indent=2), encoding="utf-8")
    comparison = {"baseline": True, "input": str(args.input), "settings": {
        "langchain": {"component": "RecursiveCharacterTextSplitter", "target_tokens": 800, "overlap": 0},
        "haystack": {"component": "RecursiveDocumentSplitter", "split_unit": "token", "target_tokens": 800, "overlap": 0,
                     "separators": ["paragraph", "sentence", "line", "space"]},
        "semantic": {"model": MODEL, "prompt": SYSTEM_PROMPT, "boundary_only": True}},
        "sections": reports}
    (output / "comparison_report.json").write_text(json.dumps(comparison, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = []
    side = []
    for report in reports:
        lines.append(f"{'=' * 66}\nSECTION\n{report['parent_title']}\nParent Tokens: {report['parent_token_count']}\n{'=' * 66}")
        side.append(lines[-1])
        parent = next(x for x in parents if x["title"] == report["parent_title"])
        paragraphs = parent["text"].split("\n\n")
        side.append("\n".join(f"P{i}\n{p}" for i, p in enumerate(paragraphs, 1)))
        for method in ("langchain", "haystack", "semantic"):
            item = report["methods"][method]
            summary = (f"{method.title()}\nChunks: {item['chunk_count']}\nTokens: " + " / ".join(map(str, item["tokens"])) +
                       f"\nBoundaries: {item['boundaries']}\nValidation: {item['validation']['result']}")
            lines.append(summary)
            side.append(summary)
        lines.append("Semantic Quality: MANUAL_REVIEW_REQUIRED\n")
    (output / "comparison_report.txt").write_text("\n\n".join(lines), encoding="utf-8")
    (output / "side_by_side.txt").write_text("\n\n".join(side), encoding="utf-8")
    with (output / "manual_review.csv").open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f)
        writer.writerow(["parent_title", "method", "chunk_count", "causal_chain_preserved", "topic_purity",
                         "standalone_understandable", "over_split", "under_split", "boundary_natural", "notes"])
        for report in reports:
            for method in ("langchain", "haystack", "semantic"):
                writer.writerow([report["parent_title"], method, report["methods"][method]["chunk_count"]] +
                                ["MANUAL_REVIEW_REQUIRED"] * 6 + [""])
    logging.info("LLM 호출 로그(API key 제외): %s", json.dumps(logs, ensure_ascii=False))
    print(f"Created baseline for {len(parents)} Parent Sections: {output}")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    main()
