import logging
import re
import unicodedata
from difflib import SequenceMatcher

LOG = logging.getLogger(__name__)
CHAPTER_RE = re.compile(r"^(\d+)\s*[장자]\s*(?:[I|]\s*)?(.*?)(?:\s*[I|])?$")
PAGE_SUFFIX_RE = re.compile(r"\s+(?:[•∙♦■.]\s*)?([\dΓ。]+)\s*$")


def normalize(text: str) -> str:
    text = unicodedata.normalize("NFKC", text).lower()
    return re.sub(r"[^0-9a-z가-힣]", "", text)


def toc_candidates(pages: list[dict]) -> list[dict]:
    candidates, chapter, prefix = [], "OTHER", ""
    for page in pages[5:17]:
        for block in page["blocks"]:
            text = block["text"].replace("\n", " ")
            if m := CHAPTER_RE.match(text):
                chapter = f"{m.group(1)}장 {m.group(2).strip()}".strip()
                prefix = ""
                continue
            page_match = PAGE_SUFFIX_RE.search(text)
            if text.startswith("경제 용어") and page_match:
                page = page_match.group(1)
                candidates.append({"chapter": chapter, "title": PAGE_SUFFIX_RE.sub("", text).strip(),
                                   "printed_page": int(page) if page.isdigit() else None,
                                   "kind": "retrieval_end"})
                continue
            if text.startswith(("차례", "머리말", "기사독해", "부록")):
                continue
            if not page_match:
                if len(text) < 35 and text.endswith(":"):
                    prefix = text + " "
                continue
            title = PAGE_SUFFIX_RE.sub("", text).strip()
            if len(normalize(title)) >= 5:
                page = page_match.group(1)
                candidates.append({"chapter": chapter, "title": prefix + title,
                                   "printed_page": int(page) if page.isdigit() else None,
                                   "kind": "section"})
                prefix = ""
    return candidates


def detect_sections(pages: list[dict], source: str) -> list[dict]:
    candidates = toc_candidates(pages)
    flat = [(p, b) for p in pages[18:] for b in p["blocks"]]
    markers, cursor = [], 0
    for candidate in candidates:
        best = None
        target = normalize(candidate["title"])
        short_target = re.sub(r"^[①②③④⑤⑥⑦⑧⑨⑩]", "", target)
        for i in range(cursor, len(flat)):
            p, block = flat[i]
            choices = [normalize(block["text"].replace("\n", " "))]
            score = max(SequenceMatcher(None, short_target, c).ratio() for c in choices)
            if score >= .88 and (best is None or score > best[0]):
                best = (score, i, p, block)
                if score == 1:
                    break
        if best:
            _, i, page, block = best
            markers.append({**candidate, "flatIndex": i, "page": page["page"]})
            cursor = i + 1
        else:
            LOG.warning("소제목 탐지 실패: %s", candidate["title"])

    sections = []
    for section_index, marker in enumerate(markers, 1):
        end = markers[section_index]["flatIndex"] if section_index < len(markers) else len(flat)
        content = flat[marker["flatIndex"] + 1:end]
        paragraphs = [b["text"] for _, b in content if b["text"]]
        if not paragraphs:
            LOG.warning("빈 Section: %s", marker["title"])
            continue
        sections.append({
            "source": source,
            "chapter": marker["chapter"],
            "sectionIndex": section_index,
            "title": marker["title"],
            "type": "OTHER",
            "pageStart": marker["page"],
            "pageEnd": content[-1][0]["page"] if content else marker["page"],
            "text": "\n\n".join(paragraphs),
            "paragraphs": paragraphs,
            "paragraphPages": [p["page"] for p, b in content if b["text"]],
        })
    return sections
