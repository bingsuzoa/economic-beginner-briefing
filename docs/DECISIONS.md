# Architecture Decisions

이 문서는 Foundation 작업 중 발생한 주요 설계 결정을 기록하기 위한 문서다.

Claude는 중요한 결정을 내릴 때 아래 형식으로 항목을 추가한다.

## ADR-001: TypeScript 모듈 시스템

### 상태

Proposed

### 결정

ESM 또는 CommonJS 중 선택 후 기록

### 이유

...

### 대안

...

### 영향

...

---

## ADR-002: Zod 스키마와 TypeScript 타입 관리 방식

### 상태

Proposed

### 결정

...

### 이유

...

### 대안

...

### 영향

...

---

## ADR-003: 날짜 라이브러리

### 상태

Proposed

### 결정

표준 Date, Temporal polyfill, date-fns-tz, Luxon 중 선택

### 필수 조건

- Asia/Seoul 기준 계산
- 테스트 가능
- 시스템 타임존 비의존
- 전날 시작·종료 시각의 명확성

### 이유

...

### 영향

...

## ADR-004: 발행 채널 제거 (Notion)

### 상태

Accepted (2026-07-25)

### 결정

Notion 발행 기능과 `publisher` 패키지 전체를 제거한다.

### 이유

- 프론트엔드가 `article_analyses` 테이블을 공개 API로 직접 조회하므로, 브리핑은 이미 파이프라인 안에서 발행된다.
- Notion을 제거하면 `BriefingPublisher` 인터페이스의 실제 구현체가 `MockBriefingPublisher`(테스트 더블) 하나만 남는다. 구현체가 없는 추상화는 유지 비용만 발생한다.
- 발행 실패가 파이프라인 전체를 FAILED로 만들던 경로도 함께 사라진다.

### 영향

- `V4__drop_publish_add_dedupe_key.sql`로 `publish_*` / `notion_*` 컬럼 제거
- `NOTION_API_KEY`, `NOTION_DATABASE_ID` 환경변수 불필요
- 다른 채널(이메일 등)이 필요해지면 새 기능으로 다시 설계한다

## ADR-005: 임베딩 저장에 pgvector를 쓰지 않는다

### 상태

Accepted (2026-07-25)

### 결정

`article_embeddings.embedding_vector`를 `vector(1536)` 대신 `TEXT`로 저장한다.
값은 pgvector 텍스트 형식(`[0.1,0.2,...]`)을 그대로 유지한다.

### 이유

- 현재 코드베이스에 벡터 연산이 전혀 없다. 유사도 검색(`<=>`), ANN 인덱스, 코사인 거리 쿼리 모두 미사용이다.
- `ArticleEmbeddingEntity`는 이미 `columnDefinition = "TEXT"`로 매핑되어 있어 애플리케이션 코드는 두 방식에 동일하게 동작한다.
- Windows용 pgvector 공식 바이너리가 없어 개발 환경마다 소스 빌드(Visual Studio Build Tools)가 필요하다. 쓰지 않는 기능에 그 비용을 강제할 이유가 없다.

### 영향

- 현재 기능 손실 없음. 1536차원 임베딩은 정상 저장·조회된다.
- **유사도 검색을 도입할 때**: pgvector 설치 → `CREATE EXTENSION vector` → `ALTER TABLE article_embeddings ALTER COLUMN embedding_vector TYPE vector(1536) USING embedding_vector::vector` 마이그레이션이 필요하다. 저장 형식이 동일해서 데이터 변환 없이 캐스팅만으로 전환된다.
