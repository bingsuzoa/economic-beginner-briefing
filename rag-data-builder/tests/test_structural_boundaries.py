import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1] / "src"))

from pdf_extractor import extract_pdf
from production_pipeline import build_sections, chunk_sections


def test_book_structural_boundaries():
    pdf = Path(__file__).parents[1] / "input" / "경제원리.pdf"
    sections, warnings = build_sections(extract_pdf(pdf), pdf.name)
    by_title = {x["title"]: x for x in sections}

    assert not [x for x in warnings if x["type"] in ("TITLE_UNRESOLVED", "DOCUMENT_END_UNRESOLVED")]
    assert "물가 결정 원리" not in by_title["⑧ 불황 구조화하는데 개혁은 지지부진"]["text"]
    assert "금융정책 구조" not in by_title["곡물 시세는 어떻게 움직이나"]["text"]
    assert "경제지표는 미시지표" not in by_title["글로벌 시장, 어디로 가고 있나"]["text"]

    recovered = by_title["'엔 저'는 왜 우리 수출에 빨간불인가"]
    assert recovered["match_method"] == "toc_page_fallback" and recovered["page_start"] == 406
    assert by_title["일본은 '엔 고를 어떻게 이겨냈나"]["page_end"] <= 406
    assert "엔 - 달러 환율이 올라" not in by_title["일본은 '엔 고를 어떻게 이겨냈나"]["text"]
    assert by_title["'엔 고가 한국 경제에는 왜 함정인가"]["page_start"] == 409

    assert "경제 용어 찾아보기" not in by_title
    assert "경제 용어 찾아보기" not in by_title["경제기사독해 테크닉 14가지"]["text"]
    chunks, _, stats, _ = chunk_sections(sections, no_llm=True)
    assert not [x for x in chunks if "경제 용어 찾아보기" in x["text"]]
    assert not [x for x in chunks if x["token_count"] > 1600]
    assert stats["validation_failures"] == 0
