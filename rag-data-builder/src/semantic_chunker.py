import json
import logging

from openai import OpenAI

LOG = logging.getLogger(__name__)
SYSTEM_PROMPT = """당신은 경제 원리 문서의 Semantic Chunk Boundary Detector다.
입력 문단을 독립적으로 이해 가능한 경제 개념 또는 경제 메커니즘 단위로 묶어라.
원인→전달 과정→결과의 인과관계와 같은 경제 메커니즘은 끊지 않는다.
반대·예외 상황이 독립 설명이면 분리할 수 있다. 키워드 변화만으로 나누지 않는다.
원문을 요약·수정·생성하지 말고 오직 연속된 문단 번호 경계만 반환한다."""
SCHEMA = {
    "type": "json_schema", "name": "chunk_boundaries", "strict": True,
    "schema": {
        "type": "object", "additionalProperties": False, "required": ["chunks"],
        "properties": {"chunks": {"type": "array", "minItems": 1, "items": {
            "type": "object", "additionalProperties": False,
            "required": ["startParagraph", "endParagraph"],
            "properties": {
                "startParagraph": {"type": "integer", "minimum": 1},
                "endParagraph": {"type": "integer", "minimum": 1},
            },
        }}},
    },
}


def semantic_boundaries(paragraphs: list[str], model: str, retries: int = 2) -> list[tuple[int, int]]:
    boundaries, _ = semantic_boundaries_with_info(paragraphs, model, retries)
    return boundaries


def semantic_boundaries_with_info(paragraphs: list[str], model: str, retries: int = 2):
    numbered = "\n\n".join(f"[P{i}] {p}" for i, p in enumerate(paragraphs, 1))
    client = OpenAI()
    for attempt in range(retries + 1):
        try:
            response = client.responses.create(
                model=model,
                input=[{"role": "system", "content": SYSTEM_PROMPT},
                       {"role": "user", "content": numbered}],
                text={"format": SCHEMA},
            )
            raw = json.loads(response.output_text)["chunks"]
            boundaries = [(x["startParagraph"], x["endParagraph"]) for x in raw]
            validate_boundaries(boundaries, len(paragraphs))
            return boundaries, {"retry_count": attempt, "validation": "PASS", "fallback": False}
        except Exception as exc:
            LOG.warning("LLM 경계 생성 실패 (%d/%d): %s", attempt + 1, retries + 1, exc)
    return [(1, len(paragraphs))], {"retry_count": retries, "validation": "SEMANTIC_CHUNKING_FAILED",
                                     "fallback": True}


def validate_boundaries(boundaries: list[tuple[int, int]], paragraph_count: int) -> None:
    expected = 1
    for start, end in boundaries:
        if start != expected or end < start or end > paragraph_count:
            raise ValueError(f"invalid boundaries: {boundaries}")
        expected = end + 1
    if expected != paragraph_count + 1:
        raise ValueError(f"missing paragraphs: {boundaries}")
