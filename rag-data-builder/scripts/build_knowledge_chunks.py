#!/usr/bin/env python3
import argparse
import json
import os
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from pdf_extractor import extract_pdf
from production_pipeline import build_sections, chunk_sections, report
from section_parser import normalize


def load_env(path):
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if line and not line.lstrip().startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                os.environ.setdefault(key.strip(), value.strip())


def write(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description="경제원리 Production Knowledge Chunk Builder")
    parser.add_argument("pdf", type=Path)
    parser.add_argument("--chapter", type=int)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--from-title")
    parser.add_argument("--section")
    parser.add_argument("--no-llm", action="store_true")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "output" / "production")
    args = parser.parse_args()
    load_env(ROOT / ".env")
    if not args.pdf.is_file():
        parser.error(f"PDF를 찾을 수 없습니다: {args.pdf}")
    if not args.no_llm and not os.getenv("OPENAI_API_KEY"):
        parser.error("OPENAI_API_KEY가 없습니다. --no-llm을 사용하거나 .env를 설정하세요")

    pages = extract_pdf(args.pdf)
    sections, warnings = build_sections(pages, unicodedata.normalize("NFC", args.pdf.name))
    if args.chapter:
        sections = [x for x in sections if x["chapter"].startswith(f"{args.chapter}장")]
    if args.from_title:
        start = next((i for i, x in enumerate(sections) if normalize(x["title"]) == normalize(args.from_title)), None)
        if start is None:
            parser.error(f"시작 Section을 찾지 못했습니다: {args.from_title}")
        sections = sections[start:]
    if args.section:
        sections = [x for x in sections if normalize(args.section) in normalize(x["title"])]
    if args.limit is not None:
        sections = sections[:args.limit]
    if not sections:
        parser.error("처리할 Section이 없습니다")

    if args.chapter or args.from_title or args.section or args.limit is not None:
        selected_titles = {x["title"] for x in sections}
        warnings = [x for x in warnings if x.get("section") in selected_titles]
    chunks, chunk_warnings, stats, decisions = chunk_sections(sections, no_llm=args.no_llm)
    warnings.extend(chunk_warnings)
    output = args.output_dir
    output.mkdir(parents=True, exist_ok=True)
    public_sections = [{k: v for k, v in x.items() if k != "paragraphs"} for x in sections]
    unresolved = sum(x["type"] == "TITLE_UNRESOLVED" for x in warnings)
    write(output / "sections.json", public_sections)
    write(output / "chunks.json", chunks)
    write(output / "chunking_report.json", report(sections, chunks, stats, decisions, unresolved))
    write(output / "warnings.json", warnings)
    preview = []
    for decision in decisions:
        preview.append(f"{'=' * 64}\nParent Section\n{decision['title']}\n"
                       f"Semantic Review: {decision['semantic_review']}\nDecision: {decision['decision']}\n"
                       f"Method: {decision['chunk_method']}\nFinal Chunk Tokens: {decision['tokens']}\n")
    (output / "preview.txt").write_text("\n".join(preview), encoding="utf-8")
    print(f"Sections: {len(sections)} | Chunks: {len(chunks)} | Warnings: {len(warnings)}\nOutput: {output}")


if __name__ == "__main__":
    main()
