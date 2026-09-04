import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1] / "src"))

from section_parser import normalize
from semantic_chunker import validate_boundaries

sys.path.insert(0, str(Path(__file__).parents[1] / "scripts"))
from chunking_poc import locate_titles
from production_pipeline import (
    Config,
    conservative_semantic,
    fallback_boundaries,
    make_chunks,
    match_titles,
    merge_tiny_boundaries,
    restore_paragraphs,
    validate_boundaries as validate_production_boundaries,
    validate_chunks,
)
from token_utils import count_tokens


def test_validation_and_normalization():
    validate_boundaries([(1, 2), (3, 4)], 4)
    assert normalize("환율 뛰면, 왜 물가도 뛰나?") == normalize("환율 뛰면 왜 물가도 뛰나")
    for bad in [[(1, 2), (4, 4)], [(1, 3), (3, 4)], [(0, 4)], [(1, 5)]]:
        try:
            validate_boundaries(bad, 4)
        except ValueError:
            pass
        else:
            raise AssertionError(f"accepted invalid boundaries: {bad}")


def test_poc_title_matching_is_ordered_and_accepts_heading_prefix():
    pages = [{"page": i + 1, "blocks": []} for i in range(18)] + [{
        "page": 126,
        "blocks": [
            {"text": "석유 정치경제학:① 미국은 왜 중동에서 싸우나"},
            {"text": "본문"},
            {"text": "석유 정치경제학:② 미국은 왜 아프가니스탄에서 싸우나"},
        ],
    }]
    targets = [
        {"chapter": "3장 물가", "title": "① 미국은 왜 중동에서 싸우나"},
        {"chapter": "3장 물가", "title": "② 미국은 왜 아프가니스탄에서 싸우나"},
    ]
    _, matches = locate_titles(pages, targets)
    assert [x["flat_index"] for x in matches] == [0, 2]
    assert all(x["match_method"] == "normalized_exact" for x in matches)


def test_production_restoration_matching_tokens_and_reconstruction():
    units = [(115, "환율이 물가를 움직"), (116, "이는 것이다."), (116, "새 설명이다.")]
    paragraphs = restore_paragraphs(units)
    assert len(paragraphs) == 2
    assert paragraphs[0]["page_start"] == 115 and paragraphs[0]["page_end"] == 116
    assert "\n\n".join(x["text"] for x in paragraphs) == "\n\n".join(x[1] for x in units)
    assert count_tokens("경제 원리", "gpt-5-mini") > 0

    pages = [{"page": i + 1, "blocks": []} for i in range(20)]
    pages[5]["blocks"] = [{"text": "3장 I 물가", "bbox": [0, 0, 0, 0]},
                           {"text": "환율 뛰면 왜 물가도 뛰나 • 115", "bbox": [0, 0, 0, 0]}]
    pages[18]["blocks"] = [{"text": "석유 정치경제학: 환율 뛰면 왜 물가도 뛰나", "bbox": [0, 0, 0, 0]}]
    _, markers, warnings = match_titles(pages)
    assert not warnings and markers[0]["match_method"] == "normalized_exact"

    section = {"section_id": "chapter3_section1", "source": "x.pdf", "chapter": "3장 물가",
               "title": "환율 뛰면 왜 물가도 뛰나", "page_start": 115, "page_end": 116,
               "paragraph_count": 2, "text": "\n\n".join(x["text"] for x in paragraphs),
               "paragraphs": paragraphs}
    chunks = make_chunks(section, [(1, 1), (2, 2)], "semantic")
    validate_chunks(section, chunks)
    assert [(x["page_start"], x["page_end"]) for x in chunks] == [(115, 116), (116, 116)]


def test_production_semantic_validation_tiny_retry_and_fallback():
    validate_production_boundaries([(1, 2), (3, 3)], 3)
    for bad in ([(1, 1), (3, 3)], [(1, 2), (2, 3)], [(1, 4)]):
        try:
            validate_production_boundaries(bad, 3)
        except ValueError:
            pass
        else:
            raise AssertionError(f"accepted invalid production boundaries: {bad}")

    class Response:
        def __init__(self, value):
            import json
            self.output_text = json.dumps(value)

    class Responses:
        def __init__(self):
            self.calls = 0

        def create(self, **_):
            self.calls += 1
            chunks = ([{"startParagraph": 1, "endParagraph": 1}, {"startParagraph": 2, "endParagraph": 3}]
                      if self.calls == 1 else [{"startParagraph": 1, "endParagraph": 3}])
            return Response({"decision": "SPLIT" if self.calls == 1 else "KEEP",
                             "chunks": chunks, "reason": "test"})

    class Client:
        def __init__(self):
            self.responses = Responses()

    paragraphs = [{"text": "짧음", "page_start": 1, "page_end": 1},
                  {"text": "경제 설명 " * 300, "page_start": 1, "page_end": 2},
                  {"text": "결과 설명 " * 300, "page_start": 2, "page_end": 2}]
    client = Client()
    boundaries, info = conservative_semantic(paragraphs, Config(semantic_max_retries=1), client)
    assert boundaries == [(1, 3)] and info["retry_count"] == 1 and client.responses.calls == 2
    fallback = fallback_boundaries(paragraphs, Config(preferred_chunk_max=400))
    validate_production_boundaries(fallback, 3)
    merged = merge_tiny_boundaries([(1, 1), (2, 2), (3, 3)], paragraphs, Config(min_child_tokens=300))
    validate_production_boundaries(merged, 3)
    assert merged[0][0] == 1 and len(merged) < 3
