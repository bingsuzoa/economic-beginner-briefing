import json
import re
import statistics
from dataclasses import dataclass
from difflib import SequenceMatcher

from haystack import Document
from haystack.components.preprocessors import RecursiveDocumentSplitter
from openai import OpenAI

from section_parser import normalize, toc_candidates
from token_utils import count_tokens

TITLE_FUZZY_THRESHOLD = 0.88
SEMANTIC_REVIEW_THRESHOLD = 1000
PREFERRED_CHUNK_MIN = 400
PREFERRED_CHUNK_MAX = 1000
MIN_CHILD_TOKENS = 300
HARD_MAX_TOKENS = 1600
SEMANTIC_MAX_RETRIES = 2
MODEL = "gpt-5-mini"

SEMANTIC_PROMPT = f"""너는 경제 RAG를 위한 보수적인 Semantic Chunk Boundary Detector다.
목표는 가능한 많은 의미 단위를 찾는 것이 아니다. 기본 결정은 KEEP이다.
각 Chunk가 Vector DB에서 독립 검색되었을 때 경제 질문에 답할 만큼 완결된 지식 단위이고,
서로 다른 검색 질문에 답하는 경제 메커니즘·독립 역사 사건·예외·정책 영향으로 명확히 전환될 때만 SPLIT한다.
정의와 설명, 배경과 핵심 원리, 원인→전달 과정→경제 주체 행동→시장 변화→결과,
하나의 질문에 대한 연속 설명, 짧은 도입이나 사례는 같은 Chunk로 유지한다.
작은 Chunk 여러 개보다 적은 수의 완결된 Chunk를 선호한다.
{MIN_CHILD_TOKENS} token 미만 Chunk는 원칙적으로 만들지 말고 짧은 도입·정의·배경을 관련 본문과 합친다.
권장 크기는 {PREFERRED_CHUNK_MIN}~{PREFERRED_CHUNK_MAX} tokens지만 의미 완결성이 크기보다 우선이다.
원문을 요약·수정·생성하지 말고 KEEP/SPLIT 결정과 연속된 문단 번호 경계만 반환한다."""

SEMANTIC_SCHEMA = {
    "type": "json_schema", "name": "conservative_chunk_boundaries", "strict": True,
    "schema": {
        "type": "object", "additionalProperties": False,
        "required": ["decision", "chunks", "reason"],
        "properties": {
            "decision": {"type": "string", "enum": ["KEEP", "SPLIT"]},
            "reason": {"type": "string"},
            "chunks": {"type": "array", "minItems": 1, "items": {
                "type": "object", "additionalProperties": False,
                "required": ["startParagraph", "endParagraph"],
                "properties": {"startParagraph": {"type": "integer", "minimum": 1},
                               "endParagraph": {"type": "integer", "minimum": 1}},
            }},
        },
    },
}


@dataclass(frozen=True)
class Config:
    title_fuzzy_threshold: float = TITLE_FUZZY_THRESHOLD
    semantic_review_threshold: int = SEMANTIC_REVIEW_THRESHOLD
    preferred_chunk_min: int = PREFERRED_CHUNK_MIN
    preferred_chunk_max: int = PREFERRED_CHUNK_MAX
    min_child_tokens: int = MIN_CHILD_TOKENS
    hard_max_tokens: int = HARD_MAX_TOKENS
    semantic_max_retries: int = SEMANTIC_MAX_RETRIES
    model: str = MODEL


def validate_boundaries(boundaries, paragraph_count):
    expected = 1
    for start, end in boundaries:
        if start != expected or end < start or end > paragraph_count:
            raise ValueError(f"invalid boundaries: {boundaries}; paragraphs={paragraph_count}")
        expected = end + 1
    if expected != paragraph_count + 1:
        raise ValueError(f"paragraph gap after {expected - 1}: {boundaries}")


def merge_tiny_boundaries(boundaries, paragraphs, config=Config()):
    merged = list(boundaries)
    while len(merged) > 1:
        tokens = [count_tokens("\n\n".join(p["text"] for p in paragraphs[s - 1:e]), config.model)
                  for s, e in merged]
        tiny = next((i for i, value in enumerate(tokens) if value < config.min_child_tokens), None)
        if tiny is None:
            break
        if tiny == 0:  # short introduction/definition belongs with its explanation
            merged[0:2] = [(merged[0][0], merged[1][1])]
        else:  # conclusion or dependent middle context belongs with what introduced it
            merged[tiny - 1:tiny + 1] = [(merged[tiny - 1][0], merged[tiny][1])]
    validate_boundaries(merged, len(paragraphs))
    return merged


def restore_paragraphs(units):
    """Group only high-confidence Korean continuations; keep original separators and order."""
    ended = re.compile(r"(?:[.!?。！？…]|(?:다|까|나|요|함|임|됨|음|셈))[\s\"'”’)]*$")
    groups = []
    for page, text in units:
        if groups and not ended.search(groups[-1]["text"]):
            groups[-1]["text"] += "\n\n" + text
            groups[-1]["page_end"] = page
        else:
            groups.append({"text": text, "page_start": page, "page_end": page})
    assert "\n\n".join(x["text"] for x in groups) == "\n\n".join(text for _, text in units)
    return groups


def match_titles(pages, threshold=TITLE_FUZZY_THRESHOLD):
    flat = [(page, block) for page in pages[18:] for block in page["blocks"]]
    markers, warnings, cursor = [], [], 0
    for candidate in toc_candidates(pages):
        target, target_norm = candidate["title"], normalize(candidate["title"])
        matches = []
        for i in range(cursor, len(flat)):
            text = flat[i][1]["text"]
            norm = normalize(text)
            if text == target:
                matches = [(1.0, i, "exact")]
                break
            if norm == target_norm or norm.endswith(target_norm):
                matches = [(1.0, i, "normalized_exact")]
                break
            score = SequenceMatcher(None, target_norm, norm).ratio()
            if score >= threshold:
                matches.append((score, i, "fuzzy"))
        if not matches:
            expected_page = candidate.get("printed_page")
            nearby = [(i, page, block) for i, (page, block) in enumerate(flat[cursor:], cursor)
                      if expected_page and abs(page["page"] - expected_page) <= 1]
            # A top-of-page block can finish the previous section; the next layout block starts the broken heading.
            fallback = next(((i, page) for i, page, block in nearby
                             if page["page"] == expected_page and block["bbox"][1] >= 120), None)
            fallback = fallback or next(((i, page) for i, page, block in nearby if block["bbox"][1] >= 120), None)
            if fallback:
                index, page = fallback
                markers.append({**candidate, "flat_index": index, "match_method": "toc_page_fallback",
                                "match_score": 0.0, "page": expected_page})
                cursor = index + 1
                continue
            warning_type = "DOCUMENT_END_UNRESOLVED" if candidate.get("kind") == "retrieval_end" else "TITLE_UNRESOLVED"
            warnings.append({"type": warning_type, "section": target, "detail": "ordered match failed"})
            continue
        matches.sort(reverse=True)
        best = matches[0]
        if best[2] == "fuzzy" and len(matches) > 1 and best[0] - matches[1][0] < .03:
            warnings.append({"type": "TITLE_UNRESOLVED", "section": target, "detail": "ambiguous fuzzy matches"})
            continue
        score, index, method = best
        markers.append({**candidate, "flat_index": index, "match_method": method,
                        "match_score": round(score, 3), "page": flat[index][0]["page"]})
        cursor = index + 1
    return flat, markers, warnings


def chapter_start(flat, previous, current):
    title = re.sub(r"^\d+\s*장\s*", "", current["chapter"])
    target = normalize(title)
    matches = []
    for i in range(previous["flat_index"] + 1, current["flat_index"]):
        text = normalize(flat[i][1]["text"])
        if text and len(text) <= max(10, len(target) * 2):
            score = SequenceMatcher(None, target, text).ratio()
            if score >= .5:
                matches.append((score, i))
    return max(matches)[1] if matches else current["flat_index"]


def build_sections(pages, source, config=Config()):
    flat, markers, warnings = match_titles(pages, config.title_fuzzy_threshold)
    chapter_starts = {}
    section_markers = [x for x in markers if x.get("kind", "section") == "section"]
    for previous, current in zip(section_markers, section_markers[1:]):
        if previous["chapter"] != current["chapter"]:
            chapter_starts[current["chapter"]] = chapter_start(flat, previous, current)
    sections = []
    for i, marker in enumerate(markers):
        if marker.get("kind") == "retrieval_end":
            continue
        end = markers[i + 1]["flat_index"] if i + 1 < len(markers) else len(flat)
        if i + 1 < len(markers) and markers[i + 1]["chapter"] != marker["chapter"]:
            end = min(end, chapter_starts.get(markers[i + 1]["chapter"], end))
        units = [(page["page"], block["text"]) for page, block in flat[marker["flat_index"] + 1:end]
                 if block["text"]]
        if not units:
            warnings.append({"type": "EMPTY_SECTION", "section": marker["title"], "detail": "no body blocks"})
            continue
        paragraphs = restore_paragraphs(units)
        text = "\n\n".join(x["text"] for x in paragraphs)
        chapter_number = next((c for c in marker["chapter"] if c.isdigit()), "0")
        section_id = f"chapter{chapter_number}_section{len(sections) + 1}"
        sections.append({
            "section_id": section_id, "source": source, "chapter": marker["chapter"],
            "title": marker["title"], "page_start": marker["page"], "page_end": paragraphs[-1]["page_end"],
            "token_count": count_tokens(text, config.model), "paragraph_count": len(paragraphs),
            "match_method": marker["match_method"], "match_score": marker["match_score"],
            "text": text, "paragraphs": paragraphs,
        })
        for p in paragraphs:
            if not re.search(r"(?:[.!?。！？…]|(?:다|까|나|요|함|임|됨|음|셈))[\s\"'”’)]*$", p["text"]):
                warnings.append({"type": "PARAGRAPH_RESTORE_SUSPECT", "section": marker["title"],
                                 "detail": p["text"][-80:]})
    return sections, warnings


def conservative_semantic(paragraphs, config=Config(), client=None, feedback=""):
    client = client or OpenAI()
    numbered = "\n\n".join(f"[P{i}] {p['text']}" for i, p in enumerate(paragraphs, 1))
    previous, errors, last_valid = None, [], None
    for attempt in range(config.semantic_max_retries + 1):
        retry = ""
        if errors:
            retry = (f"\n이전 응답 검증 실패: {errors[-1]}\n전체 문단 수: {len(paragraphs)}\n"
                     f"이전 응답: {json.dumps(previous, ensure_ascii=False)}\n누락·중복·tiny chunk 없이 수정하라.")
        try:
            response = client.responses.create(
                model=config.model,
                input=[{"role": "system", "content": SEMANTIC_PROMPT},
                       {"role": "user", "content": numbered + ("\n" + feedback if feedback else "") + retry}],
                text={"format": SEMANTIC_SCHEMA},
            )
            previous = json.loads(response.output_text)
            boundaries = [(x["startParagraph"], x["endParagraph"]) for x in previous["chunks"]]
            validate_boundaries(boundaries, len(paragraphs))
            if previous["decision"] == "KEEP" and boundaries != [(1, len(paragraphs))]:
                raise ValueError("KEEP must cover the whole section once")
            last_valid = boundaries
            tokens = [count_tokens("\n\n".join(p["text"] for p in paragraphs[s - 1:e]), config.model)
                      for s, e in boundaries]
            if len(boundaries) > 1 and any(x < config.min_child_tokens for x in tokens):
                raise ValueError(f"tiny chunks: {tokens}")
            return boundaries, {"decision": previous["decision"], "reason": previous["reason"],
                                "retry_count": attempt, "validation": "PASS", "fallback": False}
        except Exception as exc:
            errors.append(str(exc))
    if last_valid:
        merged = merge_tiny_boundaries(last_valid, paragraphs, config)
        return merged, {"decision": "SPLIT" if len(merged) > 1 else "KEEP",
                        "reason": "tiny chunks merged after semantic retries",
                        "retry_count": config.semantic_max_retries, "validation": "PASS",
                        "fallback": False, "tiny_merged": True}
    return None, {"decision": "FAILED", "reason": errors[-1],
                  "retry_count": config.semantic_max_retries, "validation": "FAILED", "fallback": True}


def fallback_boundaries(paragraphs, config=Config()):
    text = "\n\n".join(p["text"] for p in paragraphs)
    splitter = RecursiveDocumentSplitter(split_length=config.preferred_chunk_max, split_overlap=0,
                                         split_unit="token", separators=["\n\n", "sentence", "\n", " "])
    docs = splitter.run(documents=[Document(content=text)])["documents"]
    spans, offset = [], 0
    for i, p in enumerate(paragraphs, 1):
        spans.append((offset, offset + len(p["text"]), i))
        offset += len(p["text"]) + 2
    # Snap Haystack's deterministic character starts to paragraph starts so coverage stays exact.
    start_paragraphs = [1]
    for doc in docs[1:]:
        pos = doc.meta.get("split_idx_start", 0)
        paragraph = next((n for left, right, n in spans if left <= pos < right), None)
        if paragraph and paragraph != start_paragraphs[-1]:
            start_paragraphs.append(paragraph)
    return [(start, start_paragraphs[i + 1] - 1 if i + 1 < len(start_paragraphs) else len(paragraphs))
            for i, start in enumerate(start_paragraphs)]


def content_type(title, text):
    if "기사독해" in title:
        return "article_example"
    if "기사독해" in text:
        return "mixed"
    if re.search(r"(?:란 무엇인가|뜻인가|무엇인가)", title):
        return "definition"
    return "principle"


def make_chunks(section, boundaries, method, config=Config()):
    chunks = []
    for index, (start, end) in enumerate(boundaries, 1):
        selected = section["paragraphs"][start - 1:end]
        text = "\n\n".join(x["text"] for x in selected)
        chunks.append({
            "chunk_id": f"{section['section_id']}_chunk{index}",
            "parent_section_id": section["section_id"], "source": section["source"],
            "chapter": section["chapter"], "section_title": section["title"],
            "page_start": selected[0]["page_start"], "page_end": selected[-1]["page_end"],
            "chunk_index": index, "token_count": count_tokens(text, config.model),
            "chunk_method": method, "content_type": content_type(section["title"], text),
            "start_paragraph": start, "end_paragraph": end, "text": text,
        })
    return chunks


def validate_chunks(section, chunks):
    reconstructed = "\n\n".join(x["text"] for x in chunks)
    ranges = [(x["start_paragraph"], x["end_paragraph"]) for x in chunks]
    validate_boundaries(ranges, section["paragraph_count"])
    valid_pages = all(section["page_start"] <= x["page_start"] <= x["page_end"] <= section["page_end"]
                      for x in chunks)
    if reconstructed != section["text"] or not valid_pages:
        raise ValueError("parent reconstruction or page range failed")


def chunk_sections(sections, config=Config(), no_llm=False, client=None):
    chunks, warnings, stats = [], [], {"semantic_review": 0, "keep": 0, "split": 0,
                                       "retries": 0, "semantic_failures": 0, "fallback": 0,
                                       "validation_failures": 0}
    decisions = []
    for section in sections:
        if section["token_count"] <= config.semantic_review_threshold:
            boundaries, method, info = [(1, section["paragraph_count"])], "section_keep", {"decision": "KEEP", "retry_count": 0}
        else:
            stats["semantic_review"] += 1
            boundaries, info = (None, {"decision": "FAILED", "retry_count": 0, "fallback": True}) if no_llm else conservative_semantic(section["paragraphs"], config, client)
            stats["retries"] += info["retry_count"]
            if boundaries is None:
                stats["semantic_failures"] += 1
                stats["fallback"] += 1
                boundaries, method = fallback_boundaries(section["paragraphs"], config), "fallback"
                warnings.append({"type": "SEMANTIC_VALIDATION_FAILED", "section": section["title"],
                                 "detail": info.get("reason", "--no-llm fallback")})
            else:
                method = "semantic" if len(boundaries) > 1 else "section_keep"
                if info.get("tiny_merged"):
                    warnings.append({"type": "TINY_CHUNK_FORCED", "section": section["title"],
                                     "detail": "merged after semantic retries"})
            if any(count_tokens("\n\n".join(p["text"] for p in section["paragraphs"][s - 1:e]), config.model) > config.hard_max_tokens for s, e in boundaries):
                stats["fallback"] += 1
                boundaries, method = fallback_boundaries(section["paragraphs"], config), "fallback"
                warnings.append({"type": "HARD_MAX_EXCEEDED", "section": section["title"], "detail": "fallback applied"})
        section_chunks = make_chunks(section, boundaries, method, config)
        try:
            validate_chunks(section, section_chunks)
        except Exception as exc:
            stats["validation_failures"] += 1
            warnings.append({"type": "CHUNK_VALIDATION_FAILED", "section": section["title"], "detail": str(exc)})
            raise
        stats["split" if len(section_chunks) > 1 else "keep"] += 1
        decisions.append({"section_id": section["section_id"], "title": section["title"],
                          "semantic_review": section["token_count"] > config.semantic_review_threshold,
                          "decision": "SPLIT" if len(section_chunks) > 1 else "KEEP",
                          "chunk_method": method, "tokens": [x["token_count"] for x in section_chunks]})
        chunks.extend(section_chunks)
    return chunks, warnings, stats, decisions


def distribution(values):
    return {"min": min(values), "max": max(values), "avg": round(statistics.mean(values), 1),
            "median": statistics.median(values)} if values else {"min": 0, "max": 0, "avg": 0, "median": 0}


def report(sections, chunks, stats, decisions, unresolved_count=0):
    matches = {"exact": 0, "normalized_exact": 0, "fuzzy": 0, "toc_page_fallback": 0,
               "unresolved": unresolved_count}
    for section in sections:
        matches[section["match_method"]] += 1
    chunk_tokens = [x["token_count"] for x in chunks]
    return {"chapter_count": len({x["chapter"] for x in sections}), "section_count": len(sections),
            "title_matches": matches, "section_tokens": distribution([x["token_count"] for x in sections]),
            "semantic_review_sections": stats["semantic_review"], "keep_count": stats["keep"],
            "split_count": stats["split"], "final_chunk_count": len(chunks),
            "chunk_tokens": distribution(chunk_tokens), "chunks_under_300": sum(x < 300 for x in chunk_tokens),
            "chunks_over_1000": sum(x > 1000 for x in chunk_tokens),
            "chunks_over_1600": sum(x > 1600 for x in chunk_tokens),
            "semantic_retry_count": stats["retries"], "semantic_failure_count": stats["semantic_failures"],
            "fallback_count": stats["fallback"], "validation_failure_count": stats["validation_failures"],
            "sections": decisions}
