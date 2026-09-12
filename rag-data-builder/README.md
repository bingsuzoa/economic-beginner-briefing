# 경제 PDF RAG 데이터 빌더

PDF의 차례와 본문을 대조해 소제목 Section을 만들고, 800 tokens를 넘는 Section만 OpenAI Structured Outputs로 의미 경계를 판단합니다. LLM 응답은 문단 번호로만 사용하며 Chunk 본문은 원문 문단을 다시 조합합니다.

## 설치

macOS/Linux:

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```

Windows PowerShell:

```powershell
py -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env
```

`.env`에 `OPENAI_API_KEY=...`를 입력합니다.

## 실행

먼저 API 비용 없이 Section 경계를 확인할 수 있습니다.

```bash
python scripts/chunk_pdf.py input/경제원리.pdf --limit 10 --no-llm
```

API를 이용한 테스트 및 전체 실행:

```bash
python scripts/chunk_pdf.py input/경제원리.pdf --limit 10
python scripts/chunk_pdf.py input/경제원리.pdf
```

범위와 출력 위치도 지정할 수 있습니다.

```bash
python scripts/chunk_pdf.py input/경제원리.pdf --from-section 30 --to-section 40 --output-dir output/sample
```

결과는 `output/sections.json`, `output/chunks.json`에 생성됩니다. 반복 머리말·꼬리말은 추출 단계에서 제거하며, 탐지하지 못한 차례 항목과 잘못된 LLM 경계는 warning으로 남깁니다. LLM 경계가 재시도 후에도 잘못되면 해당 Section 전체를 하나의 Chunk로 보존합니다.

## 테스트

```bash
python -m pytest tests
```

Chunking과 Embedding/DB/검색 테스트를 함께 실행합니다.

## Chunking 방식 비교 PoC

3장 물가의 연속 소제목 10개만 LangChain, Haystack 2.x, 목차 기반 Custom 방식으로 비교합니다. LLM/API는 호출하지 않습니다.

```bash
python scripts/chunking_poc.py input/경제원리.pdf --chapter 3 --limit 10
python scripts/chunking_poc.py input/경제원리.pdf --section "환율 뛰면 왜 물가도 뛰나"
```

비교 결과와 수동 리뷰 CSV는 `output/poc/`에 생성됩니다.

긴 Custom Section 내부의 LangChain/Haystack/Semantic baseline 비교:

```bash
python scripts/internal_chunking_poc.py
python scripts/internal_chunking_poc.py --section "원유 값, 내리면 왜 문제인가"
```

결과는 기존 PoC를 덮어쓰지 않고 `output/internal_chunking_poc/`에 생성됩니다.

## Production Chunking Pipeline

목차 기반 Parent Section을 먼저 확정하고, 1,000 tokens를 넘는 Section만 보수적 Semantic Review합니다.

```bash
python scripts/build_knowledge_chunks.py input/경제원리.pdf --chapter 3 \
  --from-title "경기 좋아지면 왜 물가 오르나" --limit 10 \
  --output-dir output/production_test
```

API 없이 구조와 fallback만 확인하려면 `--no-llm`을 추가합니다. Semantic 실패 fallback은 PoC에서 LangChain과 같은 경계를 보였고 production에서 이미 사용하는 Haystack `RecursiveDocumentSplitter` 하나만 유지합니다.

## Embedding → pgvector → 검색

Embedding 입력은 `section_title + "\n\n" + text`이며 기본값은 `text-embedding-3-large`, 1536차원, 32개 batch입니다. `.env`의 `EMBEDDING_MODEL`, `EMBEDDING_DIMENSIONS`, `EMBEDDING_BATCH_SIZE`, `DATABASE_URL`로 변경할 수 있습니다. 모델·차원·본문 hash가 같은 결과는 `embeddings_cache.jsonl`에서 재사용합니다.

먼저 호출/비용 없이 계획과 DB 상태를 확인합니다.

```bash
python scripts/embed_knowledge_chunks.py output/production/chunks.json --dry-run
python scripts/load_knowledge_db.py output/embeddings/embedded_chunks.jsonl --dry-run
```

실행 순서는 다음과 같습니다.

```bash
python scripts/embed_knowledge_chunks.py output/production/chunks.json
python scripts/load_knowledge_db.py output/embeddings/embedded_chunks.jsonl
python scripts/search_knowledge.py "왜 환율이 오르면 물가도 오르나요?" --top-k 5
python scripts/retrieval_smoke_test.py
```

DB 사용자는 최초 1회 `CREATE EXTENSION vector` 권한이 필요합니다. 권한이 없으면 DBA가 대상 DB에서 실행해야 합니다. 마이그레이션은 `sql/001_economic_principle_chunk.sql`이며 `vector(1536)`을 사용합니다. 기본 검색은 정확한 cosine distance(`<=>`) 검색이라 ANN index를 만들지 않습니다. 데이터가 커지고 지연시간 측정상 필요할 때만 HNSW/IVFFlat을 추가합니다.

`retrieval_smoke_test.py`는 10개 질문의 Top-5 결과, distance/similarity, 원문 preview와 hit@1/3/5를 `output/retrieval_test/`에 저장합니다. Embedding 오류는 `embedding_errors.json`, 요약은 `embedding_report.json`에 남습니다.

문제 해결:

- `no credits remaining`: OpenAI 프로젝트 결제를 충전한 뒤 같은 명령을 재실행합니다. 완료 batch는 cache에서 이어집니다.
- `extension "vector" is not available`: PostgreSQL 서버에 pgvector를 설치한 뒤 extension을 생성합니다.
- 차원 불일치: `.env`의 차원과 SQL의 `vector(1536)`을 동일하게 맞춘 뒤 별도 migration을 적용합니다.
