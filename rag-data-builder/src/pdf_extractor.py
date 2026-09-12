import re
from collections import Counter
from pathlib import Path

import pymupdf


FOOTER_RE = re.compile(r"^(?:\d+|300답.*|경제기사 궁금증.*)$")


def extract_pdf(path: Path) -> list[dict]:
    """Extract layout-aware text blocks while retaining printed page labels."""
    pages = []
    with pymupdf.open(path) as doc:
        for index, page in enumerate(doc):
            raw = page.get_text("blocks", sort=True)
            printed = next(
                (int(m.group(1)) for b in raw if b[1] > page.rect.height * .88
                 if (m := re.search(r"(?:^|\n)\s*(\d+)\s*(?:\n|$)", b[4]))),
                index + 1,
            )
            lines = []
            for x0, y0, x1, y1, text, *_ in raw:
                text = clean_text(text)
                if not text or y0 > page.rect.height * .88 or FOOTER_RE.match(text):
                    continue
                lines.append({"text": text, "bbox": [x0, y0, x1, y1]})
            blocks = lines if index < 18 else merge_lines(lines)
            pages.append({"page": printed, "pdfPage": index + 1, "blocks": blocks})
    return pages


def clean_text(text: str) -> str:
    text = text.replace("\x00", "").replace("\u00ad", "")
    lines = [re.sub(r"\s+", " ", line).strip() for line in text.splitlines()]
    return "\n".join(line for line in lines if line)


def merge_lines(lines: list[dict]) -> list[dict]:
    """Rebuild paragraphs from line blocks using the book's first-line indent."""
    if not lines:
        return []
    base = Counter(round(x["bbox"][0] / 5) * 5 for x in lines).most_common(1)[0][0]
    merged = []
    for line in lines:
        gap = line["bbox"][1] - (merged[-1]["bbox"][3] if merged else -999)
        continuation = merged and gap < 15 and line["bbox"][0] <= base + 5
        if continuation:
            merged[-1]["text"] += " " + line["text"].replace("\n", " ")
            merged[-1]["bbox"][2] = max(merged[-1]["bbox"][2], line["bbox"][2])
            merged[-1]["bbox"][3] = line["bbox"][3]
        else:
            merged.append({"text": line["text"].replace("\n", " "), "bbox": line["bbox"][:]})
    return merged
