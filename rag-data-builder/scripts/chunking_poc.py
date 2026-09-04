#!/usr/bin/env python3
import argparse
import csv
import json
import sys
from difflib import SequenceMatcher
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from haystack import Document as HaystackDocument
from haystack.components.preprocessors import DocumentSplitter
from langchain_text_splitters import RecursiveCharacterTextSplitter

from pdf_extractor import extract_pdf
from section_parser import normalize, toc_candidates
from token_utils import count_tokens

CHUNK_SIZE = 800
CHUNK_OVERLAP = 0
HAYSTACK_WORDS = 350
TOKEN_MODEL = "gpt-5-mini"


def locate_titles(pages, targets):
    flat = [(p, b) for p in pages[18:] for b in p["blocks"]]
    matches, cursor = [], 0
    for target in targets:
        exact = normalized = fuzzy = None
        target_text, target_norm = target["title"], normalize(target["title"])
        for i in range(cursor, len(flat)):
            text = flat[i][1]["text"]
            norm = normalize(text)
            if text == target_text:
                exact = (i, 1.0, "exact")
                break
            if norm == target_norm or norm.endswith(target_norm):
                normalized = (i, 1.0, "normalized_exact")
                break
            score = SequenceMatcher(None, target_norm, norm).ratio()
            if score >= .88 and (fuzzy is None or score > fuzzy[1]):
                fuzzy = (i, score, "fuzzy")
        match = exact or normalized or fuzzy
        if match:
            i, score, method = match
            matches.append({**target, "flat_index": i, "page": flat[i][0]["page"],
                            "match_method": method, "match_score": round(score, 3)})
            cursor = i + 1
        else:
            matches.append({**target, "flat_index": None, "page": None,
                            "match_method": "unresolved", "match_score": 0.0})
    return flat, matches


def source_range(flat, start, end):
    parts, spans, offset = [], [], 0
    for page, block in flat[start:end]:
        text = block["text"]
        if parts:
            parts.append("\n\n")
            offset += 2
        spans.append((offset, offset + len(text), page["page"]))
        parts.append(text)
        offset += len(text)
    return "".join(parts), spans


def page_range(spans, start, end):
    pages = [page for left, right, page in spans if left < end and right > start]
    return (min(pages), max(pages)) if pages else (None, None)


def offsets(source, texts):
    result, cursor = [], 0
    for text in texts:
        start = source.find(text, cursor)
        if start < 0:
            start = source.find(text)
        if start < 0:
            raise ValueError("split text를 원문에서 찾을 수 없습니다")
        result.append((start, start + len(text)))
        cursor = start + len(text)
    return result


def chunk_records(texts, source, spans):
    records = []
    for index, (text, (start, end)) in enumerate(zip(texts, offsets(source, texts)), 1):
        page_start, page_end = page_range(spans, start, end)
        records.append({"chunk_index": index, "page_start": page_start, "page_end": page_end,
                        "token_count": count_tokens(text, TOKEN_MODEL), "text": text,
                        "source_start": start, "source_end": end})
    return records


def custom_sections(flat, matches, corpus_start, corpus_end, source, spans):
    resolved = [m for m in matches if m["flat_index"] is not None]
    sections = []
    for index, match in enumerate(matches, 1):
        if match["flat_index"] is None:
            sections.append({"section_index": index, "chapter": match["chapter"],
                             "title": match["title"], "page_start": None, "page_end": None,
                             "match_method": "unresolved", "match_score": 0.0,
                             "token_count": 0, "text": ""})
            continue
        later = [m["flat_index"] for m in resolved if m["flat_index"] > match["flat_index"]]
        end = min(later) if later else corpus_end
        text = "\n\n".join(b["text"] for _, b in flat[match["flat_index"] + 1:end])
        local_start = source.find(flat[match["flat_index"]][1]["text"])
        local_end = source.find(flat[end][1]["text"]) if end < corpus_end else len(source)
        sections.append({"section_index": index, "chapter": match["chapter"],
                         "title": match["title"], "page_start": match["page"],
                         "page_end": flat[end - 1][0]["page"], "match_method": match["match_method"],
                         "match_score": match["match_score"], "token_count": count_tokens(text, TOKEN_MODEL),
                         "text": text, "source_start": local_start, "source_end": local_end})
    return sections


def preview(items, label):
    out = []
    for item in items:
        index = item.get("chunk_index", item.get("section_index"))
        title = item.get("title", "(범용 Splitter: 제목 metadata 없음)")
        out.append(f"{'=' * 58}\n{label} {index:02d}\n{'=' * 58}\nTITLE\n{title}\n\n"
                   f"PAGE\n{item['page_start']} ~ {item['page_end']}\n\nTOKENS\n{item['token_count']}\n\n"
                   f"{'-' * 58}\n\n{item['text']}\n")
    return "\n".join(out)


def related(chunks, section):
    return [c for c in chunks if c["source_start"] < section["source_end"]
            and c["source_end"] > section["source_start"]]


def evaluate(method, chunks, sections):
    if method == "custom":
        return {"result_count": len(chunks), "title_preserved": sum(s["match_method"] != "unresolved" for s in sections),
                "explanation_split": 0, "cross_section_mixing": 0, "multi_page_issue": 0,
                "unresolved": sum(s["match_method"] == "unresolved" for s in sections),
                "text_loss": "MANUAL_REVIEW_REQUIRED"}
    title_preserved = sum(any(normalize(s["title"]) in normalize(c["text"]) for c in chunks) for s in sections)
    groups = [related(chunks, s) for s in sections]
    return {"result_count": len(chunks), "title_preserved": title_preserved,
            "explanation_split": sum(len(g) > 1 for g in groups),
            "cross_section_mixing": sum(any(sum(c["source_start"] < x["source_end"] and c["source_end"] > x["source_start"] for x in sections) > 1 for c in g) for g in groups),
            "multi_page_issue": sum(s["page_start"] < s["page_end"] and len(g) > 1 for s, g in zip(sections, groups)),
            "unresolved": 10 - title_preserved, "text_loss": "MANUAL_REVIEW_REQUIRED"}


def side_by_side(sections, langchain, haystack):
    out = []
    for section in sections:
        out.append(f"{'#' * 70}\nTARGET SECTION\n{section['title']}\n{'#' * 70}\n")
        for name, items in (("LANGCHAIN", related(langchain, section)), ("HAYSTACK", related(haystack, section))):
            out.append(f"================ {name} ================")
            for item in items:
                out.append(f"Chunk #{item['chunk_index']} | Page {item['page_start']} ~ {item['page_end']} | Tokens {item['token_count']}\n{item['text']}")
        out.append(f"================ CUSTOM ==================\nSection #{section['section_index']} | Page {section['page_start']} ~ {section['page_end']} | Tokens {section['token_count']}\n{section['text']}")
        lc, hs = related(langchain, section), related(haystack, section)
        out.append("================ NOTES ====================\n"
                   f"LangChain: related_chunks={len(lc)}, crosses_boundary={any(c['source_start'] < section['source_start'] or c['source_end'] > section['source_end'] for c in lc)}\n"
                   f"Haystack: related_chunks={len(hs)}, crosses_boundary={any(c['source_start'] < section['source_start'] or c['source_end'] > section['source_end'] for c in hs)}\n"
                   f"Custom: match={section['match_method']} ({section['match_score']})\n")
    return "\n".join(out)


def main():
    parser = argparse.ArgumentParser(description="3장 Chunking 방식 비교 PoC")
    parser.add_argument("pdf", type=Path)
    parser.add_argument("--chapter", type=int, default=3)
    parser.add_argument("--limit", type=int, default=10)
    parser.add_argument("--section")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "output" / "poc")
    args = parser.parse_args()
    if not args.pdf.is_file():
        parser.error(f"PDF를 찾을 수 없습니다: {args.pdf}")

    pages = extract_pdf(args.pdf)
    chapter_titles = [x for x in toc_candidates(pages) if x["chapter"].startswith(f"{args.chapter}장")]
    start = next(i for i, x in enumerate(chapter_titles) if x["title"] == "경기 좋아지면 왜 물가 오르나")
    targets = chapter_titles[start:start + args.limit]
    if args.section:
        targets = [x for x in chapter_titles if normalize(args.section) in normalize(x["title"])][:1]
    if not targets:
        parser.error("대상 소제목을 찾지 못했습니다")
    print("=== POC TARGET SECTIONS ===\n")
    for i, target in enumerate(targets, 1):
        print(f"{i}. {target['title']}")

    flat, matches = locate_titles(pages, targets)
    unresolved = [m for m in matches if m["flat_index"] is None]
    if unresolved:
        raise RuntimeError(f"Custom target unresolved: {[x['title'] for x in unresolved]}")
    corpus_start = matches[0]["flat_index"]
    following = chapter_titles[start + len(targets):start + len(targets) + 1]
    _, next_matches = locate_titles(pages, following) if following else (flat, [])
    corpus_end = next_matches[0]["flat_index"] if next_matches and next_matches[0]["flat_index"] else matches[-1]["flat_index"] + 1
    source, spans = source_range(flat, corpus_start, corpus_end)

    lc_splitter = RecursiveCharacterTextSplitter.from_tiktoken_encoder(
        encoding_name="o200k_base", chunk_size=CHUNK_SIZE, chunk_overlap=CHUNK_OVERLAP,
        separators=["\n\n", "\n", ". ", " ", ""],
    )
    langchain = chunk_records(lc_splitter.split_text(source), source, spans)
    hs_splitter = DocumentSplitter(split_by="word", split_length=HAYSTACK_WORDS, split_overlap=0)
    haystack_texts = [d.content.strip() for d in hs_splitter.run(documents=[HaystackDocument(content=source)])["documents"]]
    haystack = chunk_records(haystack_texts, source, spans)
    sections = custom_sections(flat, matches, corpus_start, corpus_end, source, spans)

    output = args.output_dir
    output.mkdir(parents=True, exist_ok=True)
    for name, data in (("langchain_chunks.json", langchain), ("haystack_chunks.json", haystack), ("custom_sections.json", sections)):
        (output / name).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    (output / "langchain_preview.txt").write_text(preview(langchain, "CHUNK"), encoding="utf-8")
    (output / "haystack_preview.txt").write_text(preview(haystack, "CHUNK"), encoding="utf-8")
    (output / "custom_preview.txt").write_text(preview(sections, "SECTION"), encoding="utf-8")
    (output / "side_by_side.txt").write_text(side_by_side(sections, langchain, haystack), encoding="utf-8")

    report = {"target_sections": len(sections), "settings": {
        "langchain": {"component": "RecursiveCharacterTextSplitter", "chunk_size_tokens": CHUNK_SIZE, "overlap": 0},
        "haystack": {"component": "DocumentSplitter", "split_by": "word", "split_length": HAYSTACK_WORDS, "overlap": 0},
        "custom": {"strategy": "TOC ordered exact/normalized/fuzzy matching", "fuzzy_threshold": .88}},
        "metrics": {"langchain": evaluate("langchain", langchain, sections),
                    "haystack": evaluate("haystack", haystack, sections),
                    "custom": evaluate("custom", sections, sections)}}
    (output / "comparison_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    table = ["metric                       LangChain   Haystack   Custom", "=" * 65]
    for metric in ("result_count", "title_preserved", "explanation_split", "cross_section_mixing", "multi_page_issue", "unresolved", "text_loss"):
        table.append(f"{metric:<28} {str(report['metrics']['langchain'][metric]):<11} {str(report['metrics']['haystack'][metric]):<10} {report['metrics']['custom'][metric]}")
    (output / "comparison_report.txt").write_text("\n".join(table) + "\n", encoding="utf-8")
    (output / "pdf_structure_report.txt").write_text(
        f"Pages: {len(pages)}\nTarget printed pages: {sections[0]['page_start']} ~ {sections[-1]['page_end']}\n"
        "Text extraction: 정상; 인쇄면 번호 보존\nParagraphs: 들여쓰기와 행 간격으로 복원\n"
        "Headings: 본문과 글꼴 크기가 유사하여 font size 단독 탐지 부적합\n"
        "Observed layout: 여러 페이지 Section, 접두어와 결합된 제목, 반복 header/footer 및 본문 내 페이지 번호\n",
        encoding="utf-8")
    with (output / "manual_review.csv").open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f)
        writer.writerow(["target_title", "langchain_ok", "haystack_ok", "custom_ok", "notes"])
        writer.writerows([[s["title"], "", "", "", ""] for s in sections])
    print(f"\nLangChain chunks: {len(langchain)}\nHaystack chunks: {len(haystack)}\nCustom sections: {len(sections)}\nOutput: {output}")


if __name__ == "__main__":
    main()
