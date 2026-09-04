#!/usr/bin/env python3
import argparse
import json
import logging
import os
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from pdf_extractor import extract_pdf
from section_parser import detect_sections
from semantic_chunker import semantic_boundaries
from token_utils import count_tokens

MAX_SECTION_TOKENS = 800
DEFAULT_MODEL = "gpt-5-mini"


def load_env(path: Path) -> None:
    if not path.exists():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        if line and not line.lstrip().startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            os.environ.setdefault(key.strip(), value.strip())


def write_json(path: Path, data: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def make_chunks(sections: list[dict], model: str, max_tokens: int, no_llm: bool) -> list[dict]:
    chunks = []
    for position, section in enumerate(sections, 1):
        tokens = count_tokens(section["text"], model)
        print(f"[{position}/{len(sections)}] {section['title']}\ntokens: {tokens:,}")
        semantic = tokens > max_tokens and not no_llm
        boundaries = semantic_boundaries(section["paragraphs"], model) if semantic else [(1, len(section["paragraphs"]))]
        method = "semantic" if semantic and len(boundaries) > 1 else "section"
        print(f"→ {'LLM result: ' + str(len(boundaries)) + ' chunks' if semantic else 'keep as single chunk'}")
        chapter_number = next((c for c in section["chapter"] if c.isdigit()), "0")
        for chunk_index, (start, end) in enumerate(boundaries, 1):
            text = "\n\n".join(section["paragraphs"][start - 1:end])
            paragraph_pages = section["paragraphPages"][start - 1:end]
            chunks.append({
                "id": f"chapter{chapter_number}_section{section['sectionIndex']}_chunk{chunk_index}",
                "source": section["source"], "chapter": section["chapter"],
                "section": section["title"], "sectionIndex": section["sectionIndex"],
                "chunkIndex": chunk_index, "pageStart": paragraph_pages[0],
                "pageEnd": paragraph_pages[-1], "text": text,
                "tokenCount": count_tokens(text, model), "chunkMethod": method,
            })
    return chunks


def main() -> None:
    parser = argparse.ArgumentParser(description="경제 PDF를 소제목/의미 단위로 Chunking합니다.")
    parser.add_argument("pdf", type=Path)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--from-section", type=int, default=1)
    parser.add_argument("--to-section", type=int)
    parser.add_argument("--no-llm", action="store_true")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "output")
    parser.add_argument("--max-section-tokens", type=int, default=MAX_SECTION_TOKENS)
    parser.add_argument("--model", default=DEFAULT_MODEL)
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    load_env(ROOT / ".env")
    if not args.pdf.is_file():
        parser.error(f"PDF를 찾을 수 없습니다: {args.pdf}")
    if not args.no_llm and not os.getenv("OPENAI_API_KEY"):
        parser.error("OPENAI_API_KEY가 없습니다. .env를 설정하거나 --no-llm을 사용하세요.")

    print(f"PDF loaded: {args.pdf.name}\nExtracting text...")
    pages = extract_pdf(args.pdf)
    print(f"Pages: {len(pages)}\nDetecting sections...")
    all_sections = detect_sections(pages, unicodedata.normalize("NFC", args.pdf.name))
    print(f"Sections detected: {len(all_sections)}")
    start = max(args.from_section - 1, 0)
    stop = args.to_section or len(all_sections)
    sections = all_sections[start:stop]
    if args.limit is not None:
        sections = sections[:args.limit]
    write_json(args.output_dir / "sections.json", sections)
    write_json(args.output_dir / "chunks.json", make_chunks(sections, args.model, args.max_section_tokens, args.no_llm))
    print("sections.json written\nchunks.json written")


if __name__ == "__main__":
    main()
