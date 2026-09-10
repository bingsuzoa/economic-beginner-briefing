# Thoth 일일 경제흐름 최종 설계안 v1

- 작성일: 2026-09-10
- 대상 저장소: `economic-beginner-briefing`
- 상태: 2026-09-11 구현·DB V25 마이그레이션·05시 운영창 E2E 통과
- 목표: 매일 05시 기준 직전 24시간 연합뉴스에서 의미 있는 경제 관측을 찾고, 축적된 과거 관측과 경제원리를 연결해 경제 초보자가 국가별 상황·주체의 이해관계·국가 간 관계·시장 전달 경로를 함께 이해하게 한다. 흐름 개수는 고정하지 않는다.
- 원칙: 기존 파이프라인에 단계를 추가하지 않는다. 기존 분석·검증·그래프 계층을 제거하고 더 작은 흐름으로 교체한다.
- 상세 프롬프트 계약: [ECONOMIC_FLOW_LLM_PROMPT_DESIGN_V1.md](ECONOMIC_FLOW_LLM_PROMPT_DESIGN_V1.md)
- 반복 테스트 결과: [ECONOMIC_FLOW_SMOKE_TEST_2026-09-09.md](ECONOMIC_FLOW_SMOKE_TEST_2026-09-09.md)
- 누적 관측·비용 결과: [ECONOMIC_FLOW_HISTORY_EVAL_2026-09-01_10.md](ECONOMIC_FLOW_HISTORY_EVAL_2026-09-01_10.md)

---

## 1. 결론

새 시스템은 아래 한 줄이면 된다.

```text
시간별 연합뉴스 전체 스트림 수집(코드)
→ 직전 24시간 제목 사전선별(6시간 창 4회+일일 병합 0~1회, Luna)
→ 제목/요약 정밀선별 1회(Luna)
→ 선택 기사 원문 확보·관측 추출(Luna)
→ 과거 관측·경제원리 검색(코드/DB)
→ 흐름 묶음·인과 설계(Terra 1회)
→ 설계도를 초보자용 설명으로 편집(Luna 1회)
→ 코드 검증 후 저장·노출
```

다음은 만들지 않는다.

- 기사별 최종 해설
- 기사별 Presenter
- 관계 추출기 뒤의 별도 Validator/Judge/Deduplicator
- 사건을 억지로 표준 노드와 슬롯으로 변환하는 경제 그래프
- LLM이 만든 결과를 다른 LLM이 계속 검사하는 체인
- `statementKind`, `role`, `importance`, `confidence` 같은 설명용 메타 필드
- 원문 문단별 저장 테이블
- 매시간 전체 AI 분석

경제적 판단은 Luna의 선별·추출과 Terra의 일일 설계에만 둔다. 최종 설명 편집은 값싼 Luna가 하되 Terra가 확정한 흐름을 합치거나 나누거나 근거를 다시 고르지 못한다. 이 분리는 2026-09-11 실측에서 Terra 한 호출에 판단과 작문을 함께 맡길 때보다 역할 누락을 줄였다.

---

## 2. 현재 프로젝트 진단

### 2.1 코드 규모와 실제 동작

아래는 제거 범위를 정하기 위해 기록한 2026-09-09 전환 전 체크아웃이다. 현재 구현 상태는 운영 가이드를 따른다.

| 항목 | 현재 상태 |
|---|---:|
| 운영 Java 파일 | 218개, 약 14,024줄 |
| 테스트 Java 파일 | 84개, 약 9,246줄 |
| Flyway 마이그레이션 | 23개 |
| 로컬 DB 테이블 | Flyway 이력 포함 28개 |
| `OpenAiClient`/임베딩 호출 지점 | 14곳 |
| 운영 등록 뉴스 소스 | 연합뉴스 1개 |
| 코드에 남은 비활성 뉴스 어댑터 | 9개 |

현재 `BriefingPipeline`은 Teacher·Embedding·Relevance·Diversity를 사용하지 않으면서 관련 의존성과 코드 전체를 계속 보유한다. 수집 기사를 그대로 `OpenAiNewsAnalyzer`에 넘기며, 그 내부에서는 다음 호출이 이어질 수 있다.

```text
전체 제목 선별 1회
→ 선택 기사마다 Analyzer 1회
→ 관계 추가 추출 2회
→ 관계 검증 1회
→ 그래프 저장 여부 판정 1회
→ 관계 중복 판정 1회
→ 그래프 노드 동일성 비교 0~1회
→ 그래프 역방향 탐색 판정 최대 3회
→ 경제원리 검색용 임베딩
→ Presenter 1회
→ 전체 Stage 3 분석 1회
```

선택 기사 15개라면 선택·최종 호출을 포함해 기본 호출 지점만 약 107회에 이를 수 있고, 그래프 비교·탐색과 재시도가 더해질 수 있다. 이 구조에서는 모델 하나의 프롬프트를 고쳐도 다른 단계가 다시 바꾸거나 버릴 수 있어 품질 문제의 원인을 특정하기 어렵다.

### 2.2 현재 원문 보존 문제

현재 순서는 다음과 같다.

```text
RSS 기사 저장
→ AI가 기사 선택
→ 선택 기사 본문 보강
→ 분석
```

본문을 가져오기 전에 `articles`를 저장하므로, 실제 분석에 쓴 보강 본문이 DB에 반영되지 않는다. 로컬 DB의 `articles` 3,864건 중 본문이 있는 기사는 39건이며, 연합뉴스 3,467건 중 본문이 있는 기사는 6건뿐이다.

따라서 현재 파생 데이터는 원문을 다시 확인할 수 있는 안정적인 과거 근거로 간주할 수 없다.

### 2.3 현재 DB 중복

로컬 DB의 실제 행 수는 다음과 같다. 이 값은 로컬 DB 스냅숏이며 운영 DB와 같다고 가정하지 않는다.

| 테이블 | 행 수 | 판단 |
|---|---:|---|
| `articles` | 3,864 | 유지·정리 |
| `pipeline_items` | 6,618 | 기사 필드 중복, 제거 |
| `pipeline_logs` | 1,051 | 애플리케이션 로그와 중복, 제거 |
| `pipeline_runs` | 264 | `daily_briefings`로 대체 |
| `teacher_labels` | 470 | 비활성 Teacher의 잔여 데이터, 제거 |
| `article_embeddings` | 155 | 검색에 사용되지 않는 TEXT 벡터, 제거 |
| `article_analyses` | 164 | 구형 공개 결과, 대체 후 제거 |
| `article_analyzer_results` | 1 | 중간 결과 중복, 제거 |
| `article_router_results` | 1 | 운영 경로에서 Router 제거됨, 제거 |
| `article_presentations` | 1 | 최종 결과와 중복, 제거 |
| `relation_explanation_assets` | 28 | 문자열 관계 캐시, 제거 |
| `economic_events` | 67 | 불안정한 파생 그래프, 제거 |
| `event_evidence` | 67 | 본문 전체 중복 저장, 제거 |
| `event_relations` | 46 | 새 관측 검색 방식으로 대체 |
| `event_relation_evidence` | 46 | 본문 전체 중복 저장, 제거 |
| `topics` / `topic_candidates` | 42 / 0 | 현재 목적에 필요 없음, 제거 |
| `economic_slots` / `economic_slot_values` | 7 / 20 | 상태 표준화 선행 설계, 제거 |
| `event_topics` | 6 | 그래프 제거에 따라 제거 |
| `economic_principle_chunks` | 0 | 구형 복수형 테이블, 제거 |
| `economic_principle_chunk` | 302 | 실제 벡터 검색 테이블, 유지 |

특히 경제원리 테이블의 스키마 소유자가 두 곳이다.

- Flyway의 `V23__create_economic_principle_chunk.sql`
- `rag-data-builder/sql/001_economic_principle_chunk.sql`

애플리케이션은 단수형 `economic_principle_chunk`를 조회하지만 구형 JPA 엔티티는 비어 있는 복수형 `economic_principle_chunks`를 가리킨다. 새 설계에서는 Flyway만 스키마를 소유하고 RAG 빌더는 데이터만 적재한다.

또한 로컬 DB의 Flyway 이력은 V22까지지만 단수형 테이블과 302개 청크가 이미 존재한다. 이 상태에서 V23을 그대로 실행하면 테이블 생성 충돌 가능성이 있다. 구현 시작 전에 환경별 V23 상태를 반드시 정리해야 한다.

### 2.4 현재 문서와 코드의 불일치

- `docs/PIPELINE.md`는 AI 호출이 2회라고 설명하지만 현재 코드는 다단계 기사별 호출이다.
- 같은 문서는 11개 언론사를 지원한다고 하지만 운영 Bean은 연합뉴스 하나뿐이다.
- `docs/ARCHITECTURE.md`와 `docs/SYSTEM_DESIGN_REPORT.md`에는 이미 제거된 Notion Publisher가 남아 있다.
- `docs/DATA_CONTRACTS.md`의 DTO는 현재 Java record와 일치하지 않는다.

기존 문서는 수정해서 계속 유지하기보다 이 문서, 운영 문서, 평가 문서만 남기고 제거하는 편이 안전하다. 삭제된 내용은 Git 이력으로 확인할 수 있다.

---

## 3. 새 설계가 답해야 하는 사용자 질문

매일 아침 결과는 기사 목록이 아니라 다음 질문에 답해야 한다.

1. 어제 경제에서 실제로 관측된 중요한 변화는 무엇인가?
2. 서로 다른 기사에서 나온 관측 중 무엇이 같은 흐름을 보여주는가?
3. 과거에는 이 흐름과 관련해 무엇이 관측됐는가?
4. 경제원리상 이 관측들이 어떤 경로로 이어질 수 있는가?
5. 어디까지가 기사에 나온 사실·발언·전망이고, 어디부터가 시스템의 해석인가?
6. 앞으로 무엇을 보면 이 흐름이 강화·약화·반전됐는지 알 수 있는가?

결과가 없는 날에는 흐름을 억지로 만들지 않는다. `flows: []`도 정상 성공이다.

---

## 4. 유지·제거·신규 범위

### 4.1 유지하는 제품 기능

- 로그인·계정 관리: `auth`
- 환율 조회·수집: `exchangerate`
- 연합뉴스 기사 메타데이터·본문 파싱
- 연합뉴스 본문 추출
- PostgreSQL/Flyway
- 경제원리 RAG 데이터 빌더
- JDK `HttpClient` 기반 OpenAI 호출 방식
- 정적 React 프론트엔드 서빙

환율과 인증은 경제흐름 분석과 분리된 실제 사용자 기능이므로 이번 정리에서 삭제하지 않는다. 다만 현재 `ExchangeRateBriefingService`는 제거 대상인 `article_presentations`와 `event_relation_evidence`를 직접 읽는다. 숫자 환율 기능은 유지하고, 환율 해설은 새 `daily_briefings`에서 관련 흐름을 읽도록 바꾼 뒤에만 두 구형 테이블을 삭제한다.

### 4.2 제거하는 운영 코드

| 현재 영역 | 제거 대상 | 이유 |
|---|---|---|
| `analyzer` | 기존 Analyzer, Router, 관계 추출·검증·판정·중복 제거, Presenter, Stage 3 DTO·프롬프트 | Luna/Terra 2단계로 교체 |
| `classifier` | Teacher 전체, 기사 임베딩 저장, 구형 분석·프레젠테이션·라우터·관계 캐시 엔티티 | 비활성 또는 중복 |
| `economicflow` | 사건/슬롯/토픽 그래프와 모든 resolver/judge/comparator | 불안정한 파생 그래프를 과거 기억으로 사용하지 않음 |
| `collector` | 9개 비활성 어댑터, CategoryClassifier, RelevanceScorer, DiversitySelector | 연합뉴스 전체 스트림 하나만 저장하고 Luna가 흐름 관련성을 선별 |
| `pipeline` | 단일 인터페이스 `ExecutionTracker`, `PipelineLock`, 거대한 `BriefingPipeline` | 작은 수집/일일 서비스로 교체 |
| `api` | 기사별 브리핑 API, 경제망 API | 일일 브리핑 API로 교체 |
| `reading` | 기사 읽음 이력 | 기사 카드가 일일 흐름 카드로 바뀜 |
| `frontend` | NewsCard, Teacher badge, 카테고리 badge, 경제망 화면 | 새 일일 흐름 화면으로 교체 |
| 루트 | `TestSingleArticle.java`, `test_prompt_v4.sh` | 과거 수동 디버그 잔재 |

단일 구현만 존재하는 `NewsCollector`, `NewsAnalyzer`, `PipelineLock` 같은 인터페이스도 제거한다. 테스트는 구체 서비스에 HTTP 클라이언트 fake를 주입하면 된다.

### 4.3 제거하는 의존성

현재 코드 사용 기준으로 다음 의존성은 제거할 수 있다.

- `spring-boot-starter-webflux`: JDK `HttpClient`를 이미 사용함
- `spring-retry`, `spring-aspects`: 자체 `RetryExecutor`만 사용함
- Lombok: 읽음 이력 엔티티 제거 후 사용처 없음
- `reactor-test`: WebFlux 제거 후 필요 없음

유지할 주요 의존성은 Spring Web, JPA, Security, PostgreSQL, Flyway, Rome RSS, Jackson뿐이다. pgvector는 PostgreSQL 확장과 SQL로 직접 사용하므로 Java 전용 라이브러리는 두지 않는다.

### 4.4 새로 만드는 핵심 코드

```text
article/
  ArticleEntity.java
  ArticleRepository.java
  YonhapArticleService.java
  YonhapBodyFetcher.java
  ParagraphSplitter.java

llm/
  OpenAiClient.java

briefing/
  EconomicFlowLlm.java
  ObservationStore.java
  PrincipleStore.java
  DailyBriefingEntity.java
  DailyBriefingRepository.java
  DailyBriefingService.java

api/
  DailyBriefingController.java

scheduler/
  SchedulingConfig.java
```

한 파일에서만 쓰는 요청/응답 record는 별도 DTO 파일을 만들지 않고 해당 서비스 안에 둔다.

---

## 5. 전체 실행 시점

### 5.1 시간별 수집

다음 날 한 번만 수집하면 전날 오전 기사를 놓칠 수 있으므로 수집은 계속 시간별로 한다. 입력은 경제·마켓·산업 같은 섹션별 후보가 아니라 연합뉴스 전체 뉴스 스트림 하나다. 정치·사회·세계 기사도 경제 변수의 상류 원인일 수 있기 때문이다.

```text
매시 5분
→ 연합뉴스 전체 뉴스 최신 목록 조회
→ 직전 2시간과 겹쳐 수집
→ 발행시각·URL·제목 검증
→ 원기사 키가 같은 개정판은 최신판으로 UPSERT
→ URL과 원기사 키로 중복 제거
```

이 작업에는 LLM을 사용하지 않는다. 시간별 수집은 기사를 저장할 뿐 분석하지 않는다. 두 시간 중복은 지연 색인을 흡수하고, DB UPSERT가 중복을 없앤다. 시간당 1,000건을 넘는 예외가 실제 발생하기 전에는 복잡한 커서 수집기를 만들지 않는다.

과거 일괄 수집에 사용한 연합뉴스 검색 API는 한 분류에서 100페이지 이후가 반복돼 하루 최대 1,000건 제한이 있었다. 2026-09-01~09-10 백필은 전체 검색과 공식 분류 검색을 합쳐 10,108건을 회수했지만, 기사가 많은 날에는 전체 집계보다 15~61건 적었다. 운영 시간별 수집은 최신 시점부터 2시간만 읽고 오래된 페이지에 도달하기 전에 중단하므로 이 제한을 피한다. 백필은 완전 수집으로 간주하지 않고 날짜별 회수율을 함께 기록한다.

### 5.2 오전 5시 기준 일일 분석

```text
매일 05:10 KST
→ 전일 05:00:00 이상~당일 05:00:00 미만 기사 조회
→ 속보·1보·2보와 원기사 개정판을 코드로 정리
→ Luna 제목 사전선별: 6시간 창 4개에서 각 최대 40개
→ Luna 일일 병합: 창 후보 → 최대 80개
→ 명확한 핵심 거시지표 제목을 정밀선별 입력에 코드로 보강
→ Luna 정밀선별: 제목·요약 → 최대 20개
→ 선택 기사 본문 확보
→ Luna 관측 추출
→ 관측 저장·임베딩
→ 과거 관측·경제원리 후보 조회
→ Terra planner 1회
→ Luna writer 1회
→ 코드 검증·출처 해석
→ daily_briefings 저장
```

예를 들어 9월 10일 오전 결과는 9월 9일 05:00 이상부터 9월 10일 05:00 미만까지를 다룬다. 이 경계는 00~05시에 집중되는 미국·유럽 뉴스를 같은 날 아침 브리핑에 넣기 위한 것이다. 화면 날짜와 `daily_briefings.target_date`는 결과가 공개되는 9월 10일이고, 분석 기간은 이 날짜에서 코드로 계산한다.

---

## 6. 단계 A1 — Luna 2단계 기사 선별

### 6.1 입력

원문을 주지 않는다. 먼저 같은 원기사 키의 개정판은 최신 하나만 남긴다. 다음 날 분석에서는 `[속보]`, `[1보]`, `[2보]`를 제외한다. 최초 단신이 별도 원기사 키를 쓰면 종합판과 코드상 같은 개정판으로 묶이지 않기 때문이다.

전체 제목을 6시간 창 네 개로 나눠 각 창에서 최대 40개를 남긴다. 창 후보가 80개를 넘으면 제목만 한 번 더 보내 최대 80개로 병합한다. 각 호출 안에서는 시간대 라운드로빈으로 섞어 입력 위치 편향을 줄인다.

그 뒤 소비자·생산자물가, 금리, 환율, 고용, GDP, 가계신용, 국제유가처럼 제목 자체가 명확한 핵심 거시·시장지표 기사를 코드가 정밀선별 입력에 합친다. 이는 기사를 자동 채택하는 분류기가 아니라, 대량 제목 호출에서 명백한 고용지표가 누락된 실제 실패를 막는 재현율 안전망이다. 복잡한 카테고리 계층이나 점수는 만들지 않는다.

정밀선별 호출에는 후보의 `articleId`, 제목, 발행시각, 수집 요약만 보낸다. 요약은 공백을 정리하고 최대 500자로 자른다. 여기서 최대 20개와 `reason`을 고른다.

9월 1일 1,008건의 제목·요약을 한 번에 보냈을 때 입력이 155,799토큰이었다. 제목 map-reduce와 정밀선별은 전체 원문을 합치는 방식보다 훨씬 작다. 2026-09-01~09-10 전체 뉴스 10,108건 시험에서 제목 사전선별 364,241 입력토큰, 정밀선별 108,671 입력토큰을 사용해 180건을 남겼다. 선별 비용은 10일 약 $0.1408, 하루 약 $0.0141이었다.

### 6.2 선별 기준

제목 사전선별은 원문 확인 후보 생성이고, 제목·요약 정밀선별은 최종 흐름에 포함할 기사 범위를 확정하는 단계다. 제목·요약에 다음 중 하나가 직접 나타나면 후보가 된다.

1. 금리·유동성·신용·물가·고용·소비·투자·생산·수출입·환율·재정·규제·자산가격·자금조달 중 하나의 변화
2. 큰 시장이나 공급망의 가격·수량·유인·자금조달을 바꾸는 구체적 결정
3. 위 변화의 상류 원인·제약이 되는 전쟁, 제재, 운송 차질, 자원 통제
4. 같은 입력의 다른 기사와 국가·시장·상품·정책으로 연결되고, 주체의 행동·이해관계·국가 간 관계를 설명하는 구체적 사실이나 발언

3번과 4번은 연결될 경제변수가 같은 입력의 다른 기사에 실제로 있어야 한다. 숫자가 없어도 원인과 이해관계를 복원하는 기사라면 버리지 않는다.

기업 기사도 자동 제외하지 않는다. 다음은 후보가 될 수 있다.

- 대규모 설비투자
- 회사채·대출 등 자금조달 변화
- 산업 공급능력 변화
- 가격 결정 또는 공급망 변화
- 주요 고용·임금 변화

다음은 기본적으로 제외한다.

- 금융상품 출시
- 지점 행사·캠페인
- 단순 인사·수상·협약
- 한정된 지역 지원사업의 집행 소식
- 협상 진행 자체만 전하는 노사 기사
- 경제 변수 변화가 없는 기관 발표
- 실제 변수 변화나 구체적 투자·공급 결정이 없는 단일 기업 사건
- 시장·정책 변화가 아니라 단속·적발 실적 자체를 전하는 기사

### 6.3 출력 계약

첫 호출은 `{ "selectedArticleIds": ["A001"] }`만 반환한다. 두 번째 호출은 다음을 반환한다.

```json
{"selected":[{"articleId":"A001","reason":"대규모 회사채 발행이 자금 수요와 시장금리에 연결"}]}
```

- 배열 순서는 우선순위다.
- 점수와 카테고리를 만들지 않는다.
- LLM이 같은 ID를 반복할 수 있으므로 코드는 ID를 중복 제거한다.
- 같은 원기사 번호의 속보·종합판은 Luna 전에 코드가 최신판 하나로 줄인다. 별도 기사라도 원인과 다른 시장 반응을 각각 담으면 함께 둘 수 있다.
- 선별 개수 목표는 없다. 0개도 가능하다.
- 최대 20개다. 정밀선별을 통과하고 원문에서 유효 관측이 하나 이상 나온 기사는 Terra 결과에서 반드시 한 번 이상 사용한다. Terra가 다시 중요도를 판단해 조용히 버리게 하지 않는다.
- `reason`은 제목·요약에서 발견한 관측 후보로 다음 단계에 전달한다. 근거로 확정하지 않는다.

### 6.4 모델 설정

- 모델: `gpt-5.6-luna`
- 제목 사전선별 reasoning: `low`
- 제목·요약 정밀선별 reasoning: `low`
- Structured Outputs 사용
- 429·5xx·연결 오류만 1회 재시도

이 단계의 목표는 정밀한 경제 분석이 아니라 높은 재현율로 원문 확인 대상을 줄이는 것이다.

---

## 7. 원문 확보와 구조적 문단 분할

### 7.1 본문 저장 순서 수정

선별 기사의 본문을 가져온 뒤 반드시 `articles.body`에 먼저 저장한다. 이후 Luna는 DB에 저장된 동일 본문을 입력으로 받는다.

```text
RSS_ONLY
→ 본문 추출 성공
→ FULL_TEXT 저장
→ 저장된 본문으로 문단 분할
→ Luna 추출
```

본문이 이미 `FULL_TEXT`이면 다시 내려받지 않는다. 자동으로 기존 본문을 덮어쓰지도 않는다.

### 7.2 문단 ID

서버는 저장된 본문의 줄바꿈을 기준으로 빈 문단을 제거하고 순서대로 ID를 붙인다.

```text
P001 첫 문단
P002 두 번째 문단
P003 세 번째 문단
```

의미 단위로 분할하지 않는다. LLM이 문단 경계를 결정하지 않는다.

`[`로 시작하는 사진 설명은 문단 ID는 유지하되 관측 근거로 쓰지 않는다. 기자 바이라인은 표시만 정리하고 원문 자체는 바꾸지 않는다. 사진 설명·저작권 문구를 제외한 본문 문단이 하나도 없으면 본문 실패로 본다.

문단을 별도 테이블에 저장하지 않는다. 본문은 최초 확보 후 불변으로 두고, `ParagraphSplitter` v1 규칙으로 언제든 같은 ID를 재생성한다. 분할 규칙을 바꿀 때는 기존 본문에 적용하지 않고 새 버전의 명시적 마이그레이션으로 처리한다.

### 7.3 본문 실패

- 본문 추출 실패: `body_status=FETCH_FAILED`
- RSS 요약만으로 강한 관측을 만들지 않음
- 해당 후보는 이번 흐름 입력에서 제외
- 다른 정상 기사 처리는 계속

---

## 8. 단계 A2 — Luna 관측 근거 추출

### 8.1 입력

- 기사 ID
- 제목
- 발행시각
- 선별 단계의 `reason`을 `selectionHint`로 전달
- `P001` 형식으로 번호를 붙인 전체 본문

한 요청에는 기사 하나만 넣는다. 최대 20개 기사는 서로 독립적으로 호출하고 병렬 실행할 수 있다. 긴 기사 하나가 30,000자를 넘으면 제목·첫 문단과 `selectionHint` 단어가 포함된 문단 주변만 넣고 잘린 사실을 실행 trace에 기록한다.

### 8.2 관측의 정의

관측은 기사에서 직접 확인되는 짧은 문장이다. 관측 하나는 중심 명제 하나로 완결한다. 원인·영향·발언을 포함하면 무엇의 원인·영향·발언인지 대상 지표나 결정을 같은 문장에 다시 쓴다. “영향을 설명했다”처럼 대상을 생략하지 않는다.

좋은 예:

```text
미국 기업들의 8월 회사채 발행액이 전년 동월보다 증가했다.
JP모건은 미국 10년물 국채금리가 5%에 도달할 수 있다고 전망했다.
정부는 다음 달부터 가계대출 규제를 강화할 계획이라고 밝혔다.
```

나쁜 예:

```text
채권 공급 증가로 시장 유동성이 부족해질 것이다.
AI 투자가 금리를 올렸다.
가계는 앞으로 더 어려워질 것이다.
```

뒤의 세 문장은 기사 근거를 넘어선 해석일 수 있으므로 A에서 만들지 않는다.

첫 관측은 `selectionHint`의 핵심 변화·결정을 원문에서 확인·정정한 문장으로 고정한다. 두 번째는 그 변화에 대해 기사가 명시한 원인을 우선하고, 원인이 없으면 파급·반론·불확실성을 쓴다. 세 번째는 국가·주체 간 관계와 상대의 결정권을 먼저 쓰고, 없으면 주체의 상황·목표·이해관계·정책 조건을 쓴다. 추가 수치와 전망은 앞의 내용이 없을 때만 쓴다. 서로 다른 지표의 수치와 원인을 한 관측에 섞지 않으며, 같은 대상의 제약과 대응만 220자 안에서 묶을 수 있다.

이 순서는 23:00~05:00 테스트에서 `핀란드의 투자 유치 목적`, `한국과 EU의 산업정책 충돌`, `아르헨티나 가금업계와 중국 시장의 관계`를 수치 반복 대신 보존했다. `statementKind`나 `role` 필드는 추가하지 않았다.

### 8.3 발화 성격 보존

`statementKind` 필드를 만들지 않는다. 대신 관측문 자체에 기사 표현을 보존한다.

- 실제 발표: “발표했다”, “증가했다”
- 발언자의 주장: “○○은 …라고 주장했다”
- 전망: “○○은 …라고 전망했다”
- 계획: “○○은 …할 계획이라고 밝혔다”
- 조건: “○○일 경우 …할 수 있다고 전망했다”

모델이 필드를 정확히 배치하는 것보다 최종 문장에 발화자와 서술 강도가 남는지가 평가 대상이다.

### 8.4 출력 계약

```json
{
  "observations": [
    {
      "text": "JP모건은 미국 10년물 국채금리가 5%에 도달할 수 있다고 전망했다.",
      "spanIds": ["P004", "P005"]
    }
  ]
}
```

필드는 `text`, `spanIds` 둘뿐이다. 기사 ID는 요청 하나에 이미 고정되어 있어 출력에 반복하지 않는다.

### 8.5 코드 검증

코드가 확인한다.

- `spanIds`가 해당 기사에 실제로 존재하는 문단 ID인가
- 관측문과 근거가 비어 있지 않은가
- 관측문에 나온 숫자가 선택 문단에 존재하는가
- 기사당 관측이 3개를 넘지 않는가
- 완전히 같은 관측문이 중복됐는가

코드가 확인하지 못한다.

- 선택 문단이 의미상 관측문을 충분히 지지하는가
- 더 중요한 근거를 놓쳤는가
- 전망을 사실로 바꾸지 않았는가

별도 검증 LLM은 추가하지 않는다. 한 기사의 모든 항목이 탈락하면 그 기사를 이번 Terra 입력에서 제외한다. 첫 문단을 무조건 관측으로 승격하면 사진 설명·일반 배경이 경제 근거가 될 수 있으므로 fallback 관측을 만들지 않는다. 의미 누락과 발화 강도는 고정 평가셋으로 측정한다.

숫자 검사는 보수적으로 유지하되 Luna에는 숫자와 단위를 근거 문단 표기 그대로 복사하게 한다. `3천500만`을 `3,500만`으로 바꾼 올바른 의역도 단순 문자열 검사를 통과하지 못하므로, 표현 변환을 허용하는 복잡한 숫자 파서를 추가하지 않는다.

### 8.6 모델 설정

- 모델: `gpt-5.6-luna`
- reasoning: `none`
- text verbosity: `low`
- Structured Outputs 사용
- 429·5xx·연결 오류만 1회 재시도
- 유효한 항목은 유지하고 잘못된 항목만 버림
- 기사별 성공 결과는 즉시 저장함. API 실패는 revision을 실패시키고 날짜 재실행 때 앞선 성공 관측을 재사용함

---

## 9. 관측 저장과 임베딩

유효한 관측은 `article_observations`에 저장한다. 관측 하나를 과거 검색의 기본 단위로 사용한다.

관측마다 기존 `economic_principle_chunk`와 같은 `text-embedding-3-large`, 1,536차원 임베딩을 한 번 생성한다. 서로 다른 모델의 벡터는 직접 비교하지 않는다. 여러 관측을 한 API 요청의 배열로 보내 호출 수를 줄인다.

기사 전체 임베딩은 저장하지 않는다. 기사 전체는 주제가 여러 개 섞이고 길기 때문에, 짧은 관측 임베딩이 과거 흐름 검색에 더 직접적이다.

재추출 시 같은 기사 관측은 짧은 트랜잭션 안에서 교체한다. 과거 일일 브리핑은 사용한 출처 내용을 결과 JSON에 스냅숏으로 저장하므로 이전 관측 행 교체에 영향을 받지 않는다.

---

## 10. 과거 관측 검색

### 10.1 검색 입력

현재 관측 자체의 임베딩을 사용한다. 별도 검색어 생성 LLM을 두지 않는다.

### 10.2 검색 조건

```sql
현재 관측마다
FROM article_observations past
JOIN articles a ON a.id = past.article_id
WHERE a.published_at < :window_start
ORDER BY past.embedding <=> :current_embedding
```

- 현재 분석창 시작보다 먼저 공개된 기사만 허용
- 같은 기사 제외
- 7일·30일·10년 같은 고정 기간 제한 없음
- 10년 전 관측도 의미 유사도가 높으면 후보가 될 수 있음
- 후보당 기사 제목, 발행시각, 관측문, 근거 문단 ID를 함께 반환

현재 관측마다 우선 상위 5개를 가져온다. 모든 결과를 합친 뒤 과거 관측 DB ID 기준으로 중복을 제거하고, 동일한 과거 기사에서는 유사도가 가장 높은 관측 하나만 남긴다. 남은 후보를 유사도순으로 보면서 하나의 현재 기사가 최대 2개까지만 차지하게 하고 전체 12개에서 중단한다. 현재 기사마다 과거 후보를 강제로 배정하지 않으므로 연결이 약한 기사에는 과거 관측이 없을 수 있다. 각 후보에는 검색을 일으킨 현재 관측의 C 별칭을 함께 보낸다. 이 연결은 탐색 단서일 뿐 인과 증거가 아니다. `5개`, `2개`, `12개`는 결과를 강제로 쓰는 수가 아니라 검색·입력 후보 상한이다.

### 10.3 과거 기록의 최종 사용 판단

검색 코드는 과거 기록을 “원인”으로 확정하지 않는다. Terra가 날짜와 근거를 보고 다음 중 하나를 선택한다.

- 흐름 설명에 사용
- 같은 상황의 참고로 사용
- 상충·완충 근거로 반영
- 미사용

이 분류를 별도 JSON 필드로 저장하지 않는다. 사용한 관측 ID만 결과에 남기고, 미사용 후보는 저장하지 않는다.

과거 관측 ID를 반환할 때는 explanation에 관측문에 적힌 과거 시점과 방향 또는 값을 실제 비교 문장으로 써야 한다. 기사 발행일과 지표 발생일이 다를 수 있으므로 코드가 발행일 문자열을 강제하지 않고, ID·값은 코드가 검증하고 시간 비교의 의미는 평가셋에서 확인한다.

---

## 11. 경제원리 검색

현재 `economic_principle_chunk` 302개와 pgvector 검색을 유지한다.

현재 관측 임베딩은 기사 간 연결 후보 계산에도 재사용한다. 서로 다른 기사 쌍마다 가장 유사한 관측 한 쌍만 남기고 cosine 0.30 이상인 상위 20쌍을 `connectionCandidates`로 만든다.

각 현재 관측 벡터로 경제원리 상위 2개를 조회한다. 결과를 전부 합쳐 cosine 내림차순으로 다시 정렬한 뒤 중복 제거, 0.42 하한, 최대 4개·본문 합계 4,000자 제한을 적용한다. 같은 관측 임베딩을 과거 관측, 경제원리, 기사 간 연결 후보 계산에 재사용하며 검색용 추가 LLM이나 추가 임베딩을 만들지 않는다.

`connectionCandidates`와 원리 검색 결과는 인과 판정이 아니다. 추가 임베딩이나 검색어 생성 LLM 없이 Terra의 탐색 범위를 줄이는 색인이다.

원리 검색 결과가 없으면 Terra가 일반 지식으로 구체적 사건의 원인을 만들어내면 안 된다. 현재 기사끼리 직접 연결되는 내용만 설명하거나 흐름을 만들지 않는다. 반대로 기사들이 작동 경로를 이미 직접 설명했다면 원리를 반복하지 않으며 `usedPrincipleIds`는 빈 배열이어도 정상이다.

구형 `economic_principle_chunks` JPA 엔티티와 저장소는 제거한다. `PrincipleVectorRepository` 한 경로만 남긴다.

---

## 12. 단계 B/C — Terra 설계와 Luna 편집

Terra는 경제흐름 묶음과 인과 설계만 맡고, Luna는 확정된 설계도를 초보자용 문장으로 편집한다. Terra에 근거 판단과 장문 작성을 함께 맡기지 않는다.

### 12.1 입력

- 분석창의 현재 관측 전체
- 기사 ID와 제목 맵
- 정밀선별 뒤 유효 관측이 남은 필수 기사 ID
- 기사 쌍마다 가장 유사한 현재 관측 후보 `connectionCandidates` 최대 20개
- 검색된 과거 관측, 검색을 일으킨 현재 C 별칭, 날짜·근거 문단 ID
- 검색된 경제원리 청크
- 허용된 관측 ID와 원리 청크 ID
- 독자 수준: 경제 초보자

원문 전체와 근거 문단 본문은 다시 넣지 않는다. A가 확정하고 코드가 문단 ID·숫자를 검사한 관측문과 span ID만 넣는다. 실제 문단은 서버가 최종 출처 화면을 만들 때 결합한다.

### 12.2 Terra의 책임

1. 필수 기사 ID마다 현재 관측을 적어도 하나 사용한다. 서로 직접 연결되지 않으면 억지로 묶지 않고 독립 흐름으로 쓴다.
2. 같은 흐름에 속하는 현재 관측을 묶는다.
3. `connectionCandidates`는 탐색 후보일 뿐 근거나 인과 증거로 취급하지 않는다.
4. 과거 후보가 실제로 현재 질문과 연결되는지 판단한다.
5. 같은 지표의 직전 방향 반전·반복·상충을 보여주는 과거가 있으면 현재 사실을 반복하는 일반 문장보다 그 비교를 우선한다.
6. 과거 ID를 반환할 때 설명에 과거 날짜와 방향 또는 값을 실제로 쓴다.
7. 경제원리가 두 관측 사이의 과정을 설명하는지 판단한다.
8. 단순 동시 발생을 인과관계로 만들지 않는다.
9. 원인 사건의 시점이 결과 지표의 측정기간보다 늦으면 인과로 묶지 않는다.
10. 기사에 나온 주장·전망·계획의 강도를 문장에 보존한다.
11. 기사 근거와 시스템의 경제적 해석을 문장에서 구분한다.
12. 각 흐름의 `관측된 변화 → 국가·기업·가계의 상황과 목표·제약 → 협력·경쟁·대립 관계 → 가격·무역·공급·투자·금리·고용의 전달 경로`를 3~8문장짜리 `connection`으로 설계한다.
13. 제목·독자용 설명·watch point는 쓰지 않는다.
14. 원문에서 유효 관측이 남지 않은 기사는 Terra 입력 전에 제외한다. Terra가 입력받은 필수 기사를 다시 임의로 탈락시키지는 않는다.
15. 근거 강도가 비슷하면 서로 다른 기사의 관측이 원인→파급으로 연결되는 흐름을 단일 기사 요약보다 우선한다. 기사 수 자체를 품질로 보지는 않는다.
16. 설명에서 작동 원리를 실제로 사용한 청크 ID만 반환한다. 주제가 비슷하다는 이유만으로 원리 ID를 붙이지 않는다.
17. 같은 충격의 외교·안보 관측은 가격의 직접 원인으로 단정하지 않고 관련국의 입장과 해결 제약을 보여주는 배경으로만 쓴다.
18. 개별 범죄 단속·자산 동결, 신제품 사양, 행사·개인 일화는 현재 근거에 더 넓은 가격·수요·공급·정책 변화가 없으면 흐름으로 만들지 않는다.

### 12.3 Terra 출력 계약

```json
{
  "flows": [
    {
      "observationIds": ["C01", "C02", "H01"],
      "principleIds": ["K01"],
      "connection": "..."
    }
  ]
}
```

필요한 필드만 둔다.

- `observationIds`: 현재 `C`와 과거 `H` 관측을 함께 추적하며 흐름마다 `C`가 최소 하나 필요
- `principleIds`: 실제 연결에 사용할 일반 경제원리
- `connection`: 선택한 ID만으로 성립하는 경제 논리

`role`, `statementKind`, `confidence`, `importance`, `category`는 사용하지 않는다.

### 12.4 Luna 편집

Luna는 흐름 순서와 개수를 그대로 유지하고 flow별 제목, 5~12문장 설명, watch point 0~2개만 쓴다. 근거 선택과 경제 판단을 다시 하지 않으며, 서로 다른 관측을 결합해 도출한 파급은 이미 발생한 사실이 아니라 가능성·압력·조건으로 표현한다.

### 12.5 출력 수

- 최소 0개, 개수 상한 없음
- 같은 관측을 서로 다른 흐름에 반복 사용하지 않는 것이 기본
- 근거가 있는 독립 흐름은 모두 반환하되 경제적 파급 범위·규모·지속성·기사 간 연결 강도로 정렬
- 개수 대신 Terra 설계 출력과 Luna 전체 출력 예산으로 토큰을 통제
- 화면은 중요도순 흐름 전체를 노출

### 12.6 모델 설정

- 모델: `gpt-5.6-terra`
- reasoning: `medium`
- text verbosity: `low`
- 일일 정상 경로 1회
- 입력 상한: 15,000토큰, `max_output_tokens`: 4,000
- Luna writer: `gpt-5.6-luna`, reasoning `none`, `max_output_tokens` 6,000
- 구조 오류는 자동 재시도하지 않고 실패 plan을 trace에 기록
- `max_output_tokens` 때문에 `incomplete`면 자동 재호출하지 않고 실패로 기록
- `prompt_cache_options.mode=explicit`, cache breakpoint 없음

품질이 떨어지면 먼저 같은 고정 평가에서 Terra reasoning `high`가 반복 개선되는지 확인한다. 정상 스케줄의 모델·reasoning을 자동 승격하지 않는다.

---

## 13. 최종 코드 검증

Terra와 Luna 뒤에 검증 LLM을 추가하지 않는다. 서버가 다음만 검사한다.

- `flows`가 배열인가
- Terra의 연결 논리와 Luna의 제목·설명이 비어 있지 않은가
- 모든 `usedObservationIds`가 입력 허용 목록에 있는가
- 모든 `usedPrincipleIds`가 입력 허용 목록에 있는가
- 흐름마다 현재 분석창의 관측이 최소 하나 있는가
- 필수 기사마다 현재 관측이 결과 전체에서 최소 하나 사용됐는가
- 동일 관측 ID가 한 흐름 안에서 중복됐는가
- 결과 전체가 출력 길이 제한을 지키는가

허용 목록 밖 ID가 있거나 현재 근거가 하나도 없는 흐름은 저장하지 않는다. 그러나 필수 기사 누락은 개별 흐름 오류가 아니라 결과 전체의 불완전성이므로 해당 revision을 `FAILED`로 기록하고 공개하지 않는다. 별도 검증 LLM이나 자동 재작성 호출은 추가하지 않는다. 이전 SUCCESS revision이 있으면 그대로 유지하고, 관리자가 원인을 확인한 뒤 날짜 전체를 재실행한다.

검증이 통과한 뒤 서버가 ID를 실제 기사 제목·URL·발행시각·관측문·문단 텍스트로 해석해 공개용 JSON을 만든다. 같은 기사의 여러 관측은 출처 하나 아래에 묶는다. 모델이 URL이나 날짜를 복사하게 하지 않는다.

---

## 14. 최종 DB 설계

경제흐름 분석에 필요한 테이블은 네 개뿐이다.

```text
articles
article_observations
economic_principle_chunk
daily_briefings
```

인증과 환율 테이블은 별도 기능으로 유지한다.

### 14.1 `articles` — 기존 테이블 정리

```sql
CREATE TABLE articles (
  id               VARCHAR(64) PRIMARY KEY,
  source           VARCHAR(128) NOT NULL,
  title            TEXT NOT NULL,
  summary          TEXT NOT NULL DEFAULT '',
  body             TEXT,
  body_status      VARCHAR(16) NOT NULL DEFAULT 'RSS_ONLY',
  url              TEXT NOT NULL UNIQUE,
  published_at     TIMESTAMPTZ NOT NULL,
  collected_at     TIMESTAMPTZ NOT NULL,
  body_fetched_at  TIMESTAMPTZ,
  content_hash     VARCHAR(64),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CHECK (body_status IN ('RSS_ONLY', 'FULL_TEXT', 'FETCH_FAILED'))
);

CREATE INDEX idx_articles_published_at
  ON articles (published_at DESC);
```

현재 전 행이 NULL인 `source_article_id`, 전 행이 NULL인 `author`, 상수인 `language`, 새 흐름에서 쓰지 않는 `category`는 제거한다.

`articles.id`는 개정판 CID가 아니라 `YONHAP:{원기사 키}`로 서버가 만든 안정 ID다. 같은 원기사의 후속 판은 이 ID로 제목·요약·URL·발행시각만 갱신한다. 본문은 다음 날 최종 판에서 처음 확보하므로 개정판마다 행을 늘리는 `story_key` 컬럼을 별도로 만들지 않는다.

기존 기사 원문은 삭제하지 않는다. 다만 본문이 없는 기존 기사는 `RSS_ONLY`로 표시하며 과거 관측 근거로 사용하지 않는다.

### 14.2 `article_observations` — 새 과거 기억

```sql
CREATE TABLE article_observations (
  id                  VARCHAR(80) PRIMARY KEY,
  article_id          VARCHAR(64) NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
  observation_no      SMALLINT NOT NULL,
  observation_text    TEXT NOT NULL,
  source_span_ids     TEXT[] NOT NULL,
  embedding           vector(1536),
  embedding_model     VARCHAR(64),
  model_name          VARCHAR(64) NOT NULL,
  prompt_version      VARCHAR(32) NOT NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (article_id, observation_no),
  CHECK (cardinality(source_span_ids) > 0)
);
```

ID는 서버가 `articleId:O1`, `articleId:O2`처럼 만든다. LLM이 만들지 않는다.

초기 데이터 규모에서는 ANN 인덱스를 만들지 않고 정확한 cosine 검색을 사용한다. 관측이 수십만 건으로 늘고 실제 측정에서 느릴 때만 HNSW를 추가한다.

임베딩 호출만 실패한 경우 관측과 출처는 저장하고 embedding은 NULL로 둔다. 그 관측은 현재 Terra 입력에는 사용할 수 있지만 과거·원리·현재 연결 검색에서는 제외한다. 다음 일일 실행 전에 NULL 행만 한 번 보충한다.

### 14.3 `economic_principle_chunk` — 기존 단수형 유지

현재 RAG 빌더가 적재한 단수형 테이블을 유지한다. 스키마는 Flyway가 만들고 `rag-data-builder`는 INSERT/UPDATE/DELETE만 수행한다.

### 14.4 `daily_briefings` — 결과와 실행 이력 통합

```sql
CREATE TABLE daily_briefings (
  id                VARCHAR(64) PRIMARY KEY,
  target_date       DATE NOT NULL,
  revision          INTEGER NOT NULL,
  status            VARCHAR(16) NOT NULL,
  trigger_type      VARCHAR(16) NOT NULL,
  pipeline_version  VARCHAR(32) NOT NULL,
  models_json       JSONB NOT NULL,
  usage_json        JSONB NOT NULL DEFAULT '{}',
  trace_json        JSONB NOT NULL DEFAULT '{}',
  result_json       JSONB,
  input_hash        VARCHAR(64),
  error_message     TEXT,
  started_at        TIMESTAMPTZ NOT NULL,
  finished_at       TIMESTAMPTZ,
  UNIQUE (target_date, revision),
  CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX idx_daily_briefings_latest
  ON daily_briefings (target_date DESC, revision DESC)
  WHERE status = 'SUCCESS';
```

`daily_briefings`가 기존 `pipeline_runs`, `pipeline_logs`, `pipeline_items`, 세 종류의 분석 결과 테이블을 대체한다.

`target_date`는 오전 결과가 공개되는 날짜다. 분석창은 항상 `target_date - 1일 05:00` 이상부터 `target_date 05:00` 미만으로 계산하므로 시작·종료 시각을 중복 컬럼으로 저장하지 않는다.

`trace_json`에는 디버깅에 필요한 최소 정보만 둔다.

```json
{
  "windowArticleCount": 1170,
  "selectedArticleIds": ["..."],
  "observationIds": ["..."],
  "historyCandidateIds": ["..."],
  "principleChunkIds": ["..."]
}
```

원문이나 전체 LLM 응답을 다시 복제하지 않는다.

`usage_json`에는 실제 API 응답의 토큰을 합산한다.

```json
{
  "luna": {"input": 85383, "cacheWriteInput": 0, "cachedInput": 0, "output": 8122},
  "terra": {"input": 7930, "cacheWriteInput": 0, "cachedInput": 0, "output": 978},
  "embedding": {"input": 4441}
}
```

---

## 15. 삭제할 DB 테이블

새 경로가 검증된 뒤 아래 21개를 제거한다.

```text
article_analyses
article_analyzer_results
article_embeddings
article_presentations
article_reading_history
article_router_results
economic_events
economic_principle_chunks
economic_slot_values
economic_slots
event_evidence
event_relation_evidence
event_relations
event_topics
pipeline_items
pipeline_logs
pipeline_runs
relation_explanation_assets
teacher_labels
topic_candidates
topics
```

기존 파생 데이터는 새 관측으로 변환하지 않는다. 현재 저장 과정에서 원문 전체가 근거로 들어가거나 모든 관계가 `FACT`·confidence 1로 저장된 경우가 있어 새 시스템의 신뢰 가능한 근거로 승격할 수 없기 때문이다.

삭제 전에는 테이블별 CSV 또는 `pg_dump`를 한 번 보관한다. 백업은 롤백용이지 새 검색 입력이 아니다.

---

## 16. OpenAI 클라이언트 변경

현재 `OpenAiClient`는 Chat Completions 응답에서 문자열만 반환한다. 토큰을 로그에는 출력하지만 호출자와 DB에는 전달하지 않는다.

새 클라이언트는 JDK `HttpClient` 한 클래스로 유지하되 Responses API의 구조화 출력을 사용하고 다음 결과를 반환한다.

```java
record LlmResult<T>(
    T value,
    int inputTokens,
    int cacheWriteInputTokens,
    int cachedInputTokens,
    int outputTokens
) {}
```

- 모델별 호출 메서드를 여러 개 만들지 않는다.
- 요청마다 모델, reasoning effort, 출력 스키마, 출력 상한만 받는다.
- 전체 원문과 LLM 원문 응답을 INFO 로그에 기록하지 않는다.
- 429·5xx·일시적 네트워크 오류만 한 번 재시도한다.
- 구조·의미 검증 실패는 추가 토큰을 쓰는 자동 수정 요청 없이 revision을 실패시킨다.
- 토큰 상한은 프롬프트 지시가 아니라 호출 전 서버 계산과 `max_output_tokens`로 강제한다.
- 기본 호출은 `prompt_cache_options.mode=explicit`이고 breakpoint는 없다. 매일 달라지는 기사 입력의 자동 cache write를 막는다.

OpenAI 공식 문서 기준으로 Luna는 비용 민감·대량 작업에, Terra는 비용과 지능의 균형이 필요한 작업에 맞는 모델이다.

- Luna: <https://developers.openai.com/api/docs/models/gpt-5.6-luna>
- Terra: <https://developers.openai.com/api/docs/models/gpt-5.6-terra>

### 16.1 설정값

현재 `teacher`, `diversity`, `economic-flow-comparison-model`, `economic-flow-traversal-model` 설정은 제거하고 실제로 조정할 값만 남긴다.

```yaml
briefing:
  scheduler:
    collect-cron: "0 5 * * * *"
    daily-cron: "0 10 5 * * *"
  budget:
    screening-input-tokens: 100000
    extraction-input-tokens: 100000
    synthesis-input-tokens: 15000
    daily-cost-usd: 0.14

openai:
  api-key: ${OPENAI_API_KEY}
  screening-model: gpt-5.6-luna
  extraction-model: gpt-5.6-luna
  synthesis-model: gpt-5.6-terra
  embedding-model: text-embedding-3-large
```

reasoning effort와 프롬프트 버전은 각 호출 서비스의 상수로 시작한다. 실제로 운영 중 자주 바꿔야 한다는 증거가 생기기 전에는 모든 세부값을 환경변수로 만들지 않는다.

---

## 17. API 설계

### 17.1 공개 API

```http
GET /api/briefings/latest
GET /api/briefings/2026-09-08
```

응답은 `daily_briefings.result_json`의 공개 스냅숏이다.

```json
{
  "targetDate": "2026-09-08",
  "generatedAt": "2026-09-09T05:12:10+09:00",
  "title": "9월 8일 경제흐름",
  "flows": [
    {
      "id": "briefing-id:F1",
      "title": "...",
      "explanation": "...",
      "watchPoints": ["..."],
      "sources": [
        {
          "articleId": "...",
          "title": "...",
          "source": "연합뉴스",
          "url": "https://...",
          "publishedAt": "...",
          "observation": "...",
          "evidence": [
            {"spanId": "P004", "text": "..."}
          ]
        }
      ],
      "principles": [
        {"chunkId": "...", "source": "...", "section": "..."}
      ]
    }
  ]
}
```

### 17.2 관리자 API

```http
POST /api/admin/briefings/2026-09-08/run
GET  /api/admin/briefings/runs
GET  /api/admin/briefings/runs/{runId}
```

기사별 재분석 API는 초기 운영에서 제거한다. 흐름은 하루 입력 전체를 기준으로 만들어지므로 기사 한 건만 다시 돌려 기존 일일 결과에 끼워 넣으면 결과 일관성이 깨진다. 재분석은 해당 날짜 전체를 새 revision으로 만든다.

---

## 18. 프론트엔드 변경

오늘의 토트는 기사 목록이 아니라 흐름 목록을 보여준다.

```text
9월 8일 경제흐름

[흐름 1 제목]
경제 흐름 설명
앞으로 볼 것
사용한 기사·근거 펼치기

[흐름 2 제목]
...
```

제거한다.

- 기사별 카테고리 badge
- Teacher label/confidence
- confirmed/proposed/expected badge
- 기사별 읽음 이력과 3초 IntersectionObserver
- 별도 `/presentation` 호출
- 기존 토트 경제망 탭과 3D 그래프

발화 성격은 badge가 아니라 본문 문장에 보존한다. 예를 들어 전망이라면 제목과 설명에서도 “전망했다”, “가능성이 제기됐다”라고 쓴다.

경제망 화면이 다시 필요해지면 먼저 `daily_briefings`와 관측으로 검증된 흐름을 시각화한다. 현재처럼 별도 LLM 그래프를 병행 저장하지 않는다.

---

## 19. 실패·재시도·부분 성공

| 실패 | 동작 |
|---|---|
| RSS 한 피드 실패 | 다른 피드 저장, 애플리케이션 로그 기록 |
| Luna 선별 API 실패 | 429·5xx·연결 오류만 1회 재시도, 계속 실패하면 revision 전체 실패 |
| 본문 일부 실패 | 해당 기사 제외, 나머지 계속 |
| Luna 관측 일부 span 오류 | 해당 관측만 제외, 전부 탈락하면 해당 기사 제외 |
| Luna 관측 API 실패 | 중요 기사 누락을 숨기지 않고 revision 전체 실패. 재실행 때 앞선 성공 관측 재사용 |
| 임베딩 실패 | 현재 관측은 유지하고 과거·원리 검색 없이 현재 관측만으로 계속 |
| 과거 관측 없음 | 현재 관측+경제원리로 Terra 실행 |
| 경제원리 없음 | 현재 기사끼리 직접 연결 가능한 범위만 허용 |
| Terra ID·coverage·구조 오류 | 잘못된 plan을 trace에 남기고 revision 전체 실패 |
| Luna writer ID·순서·숫자 오류 | revision 전체 실패 |
| 유효 흐름 0개 | `SUCCESS`, `flows: []` 저장 |

LLM을 기다리는 동안 DB 트랜잭션을 열어두지 않는다.

---

## 20. 멱등성과 동시 실행

- 기사 수집: `articles.url` UNIQUE와 `ON CONFLICT DO NOTHING`
- 관측: `(article_id, observation_no)` UNIQUE, 기사 단위 교체
- 일일 결과: `(target_date, revision)` UNIQUE
- 스케줄 재실행: 같은 날짜에 SUCCESS가 있으면 기본적으로 건너뜀
- 관리자 재실행: revision을 증가시켜 새 결과 생성
- 공개 API: 해당 날짜의 가장 최신 SUCCESS revision만 반환
- 단일 Windows 서비스인 현재 운영에서는 `DailyBriefingService`의 `AtomicBoolean`로 중복 실행을 막음
- 다중 인스턴스로 전환할 때만 PostgreSQL advisory lock으로 교체

시간별 수집과 일일 분석은 겹쳐 실행될 수 있으므로 서로를 전역 잠금으로 막지 않는다. 05:10 분석은 05:05 수집이 끝낸 05:00 미만 기사만 읽는다.

---

## 21. 토큰·비용 상한

기사 수가 아니라 실제 입력 예산을 제한한다.

| 단계 | 일일 상한 |
|---|---:|
| Luna 제목 사전선별 입력 | 50,000토큰 |
| Luna 제목·요약 정밀선별 입력 | 20,000토큰 |
| Luna 관측 추출 입력 | 100,000토큰 |
| Terra 입력 | 15,000토큰 |
| Terra 설계 출력+reasoning | 4,000토큰 |
| Luna 편집 출력+reasoning | 6,000토큰 |
| Terra 정상 호출 | 1회 |

2026-09-01~09-10의 8,500건을 수집해 원기사 기준 8,011건으로 줄이고 최종 설계를 한 번 적용한 실측은 다음과 같다. 9월 10일은 20:23까지의 부분 스냅숏이다.

| 구성 | 10일 입력 / 출력 토큰 | 10일 비용 | 일평균 |
|---|---:|---:|---:|
| Luna 제목 사전선별 | 265,763 / 8,667 | $0.0636 | $0.0064 |
| Luna 제목·요약 정밀선별 | 121,287 / 11,574 | $0.0381 | $0.0038 |
| Luna 기사별 추출 198회 | 248,026 / 39,455 | $0.0970 | $0.0097 |
| 현재 관측 임베딩 | 45,217 / - | $0.0059 | $0.0006 |
| Terra 최종 10회 | 78,545 / 26,462 | $0.4746 | $0.0475 |
| 합계 | 758,838 / 86,158 | **$0.6792** | **$0.0679** |

이 표는 초기 8,500건 경로의 완전 실행값이다. 이후 전체 뉴스 10,108건에 제목 map-reduce와 정밀선별을 적용한 선별 비용은 10일 $0.1408, 하루 $0.0141이었다. 나머지 단계 밀도가 같다고 가정한 전체 스트림 예상은 하루 약 $0.071, 30일 약 $2.13이다. 09-09 05:00~09-10 05:00 운영창의 최종 E2E 비용은 $0.0723이었다. 일일 비용 차단선은 $0.14로 시작하고 7일 usage로 조정한다. 상세 계산과 폐기 실험 비용은 누적 평가 문서에 기록한다.

모든 합격 실행 호출의 cache write와 cache hit는 0이었다. 고유한 일일 원문을 재사용 가능성이 없는 캐시에 쓰지 않아 cache write 1.25배 비용을 피했다.

서버는 우선순위가 낮은 검색 후보부터 제외해 Terra 입력을 상한 안에 맞춘다. 현재 관측과 그 근거만으로도 상한을 넘거나 예상 일일 비용이 설정 상한을 넘으면 Terra를 호출하지 않고 run을 `FAILED`로 기록한다.

---

## 22. 모델 검증 계획

2026-09-01~09-10 순차 누적 테스트와 전체 뉴스 제목 선별에 이어 실제 Java 운영 경로로 2026-09-10 05:00~09-11 05:00 창을 재현했다. 1,166건 수집본을 원기사·얇은 속보 기준 1,091건으로 정리하고, 84개 사전 후보에서 15개 기사와 43개 관측을 확정했다. 첫 결과가 국가별 기사 요약 12개로 분절되는 문제를 확인해 Terra의 공통 충격·공통 최종수요 묶음 우선순위를 강화했고, 재실행 결과는 중동 충돌→에너지 공급→미국·유럽·한국의 물가·금리·환율, AI 수요→반도체·자금·전력 병목 등 4개 구조적 흐름으로 정리됐다. 최종 실행 비용은 $0.06685, coverage와 ID·숫자·근거 문단 검증 오류는 0건이었다. 한 날짜 과적합을 막기 위해 실제 날짜 20일 이상으로 계속 검증한다.

### 22.1 A 평가

사람이 검토한 기사 200~300건으로 Luna를 평가한다.

- 경제흐름에 필요한 기사 누락
- 불필요한 기사 선택
- 관측문과 원문 문단의 일치
- 주장·전망·계획의 표현 보존
- 숫자·단위·주체 오류

9월 9일에는 모델을 Terra로 올려도 누락이 안정적으로 해결되지 않았다. 먼저 `selectionHint → 기사별 원문 확인` 계약이 지켜지는지 고친다. 그 구조에서도 반복 실패할 때만 같은 평가셋으로 A2의 Terra 승격을 비교한다.

### 22.2 C 평가

동일한 일일 입력 10~20개를 Terra `medium`과 `high`에 넣고 설정을 숨긴 채 비교한다.

- 흐름 선택의 경제적 의미
- 인과관계의 근거성
- 과거 기록의 관련성
- 경제원리 적용 정확성
- 사실과 시스템 해석 구분
- 초보자 이해도

Terra medium이 반복적으로 중요한 연결을 놓치거나 잘못된 인과를 만들고 high가 일관되게 개선할 때만 정상 reasoning 승격을 검토한다.

### 22.3 배포 금지 오류

고정 평가셋에서 다음이 한 건이라도 재현되면 원인을 고치기 전 배포하지 않는다.

- 전망을 이미 일어난 사실로 표현
- 원문에 없는 수치·사건 생성
- 서로 다른 국가·만기·기업을 같은 대상으로 취급
- 미래 기사를 과거 원인으로 사용
- 경제원리를 실제 발생 원인으로 단정
- 출처 ID가 실제 근거와 불일치

모든 미래 기사에서 완벽함을 보장할 수는 없다. 대신 실패를 한 단계에서 재현하고 수정할 수 있어야 한다.

---

## 23. 구현·전환 순서

논리 설계는 기존 파생 구조가 없는 상태를 기준으로 하지만, 실제 운영 전환에서 테이블을 먼저 삭제하면 서비스가 중단된다. 물리적 삭제는 다음 순서로 한다.

### Phase 0 — 백업과 Flyway 정리

1. 운영·개발 DB별 테이블과 Flyway 이력을 확인한다.
2. 전체 `pg_dump`를 만든다.
3. V23 적용 여부와 수동 생성된 `economic_principle_chunk`를 정리한다.
4. 기존 테스트를 한 번 실행해 전환 전 기준을 남긴다.

적용된 Flyway 파일을 바로 삭제하거나 수정하지 않는다. 기존 환경의 checksum 검증이 깨질 수 있기 때문이다. 기존 마이그레이션은 이력으로 유지하고 새 마이그레이션에서 테이블을 정리한다.

### Phase 1 — 새 테이블과 수집 분리

1. `articles`에 본문 상태 컬럼을 추가한다.
2. `article_observations`, `daily_briefings`를 만든다.
3. 시간별 스케줄러를 기사 저장 전용으로 바꾼다.
4. 선택 본문이 저장된 뒤 분석되는지 테스트한다.

### Phase 2 — Luna 파이프라인

1. 제목 사전선별과 제목·요약 정밀선별, 두 최소 JSON Schema를 구현한다.
2. 문단 분할기와 관측 추출기를 구현한다.
3. 관측 span 코드 검증을 구현한다.
4. 관측 임베딩 저장을 구현한다.

### Phase 3 — 검색과 Terra

1. 과거 관측 vector 검색을 구현한다.
2. 경제원리 vector 검색을 재사용한다.
3. 토큰 예산 입력 packer를 구현한다.
4. Terra planner 1회, Luna writer 1회와 코드 검증을 구현한다.
5. `daily_briefings`에 usage·trace·result를 저장한다.

### Phase 4 — 새 API와 화면

1. `/api/briefings/latest`, 날짜 조회 API를 만든다.
2. 일일 흐름 목록 화면으로 교체한다.
3. 출처·근거 펼침을 연결한다.
4. 구형 경제망과 기사별 presentation 호출을 화면에서 제거한다.

### Phase 5 — 그림자 평가

1. 기존 화면을 유지한 채 새 파이프라인을 10~20일 수동 실행한다.
2. 사용자와 결과를 검토한다.
3. Luna 추출과 Terra medium/high 비교 결과로 설정을 확정한다.
4. 실제 일일 비용과 완료 시각을 확인한다.

### Phase 6 — 컷오버와 삭제

1. 새 화면을 기본으로 전환한다.
2. 구형 스케줄러 호출을 중지한다.
3. 구형 파생 테이블을 백업한다.
4. 구형 코드·테스트·문서를 삭제한다.
5. 21개 구형 테이블을 FK 역순으로 DROP한다.
6. 7일간 오류·비용을 관찰한다.

---

## 24. 최소 테스트

삭제되는 클래스별 테스트를 그대로 이식하지 않는다. 새 위험 경계에 맞춰 작게 다시 만든다.

### 단위 테스트

- Yonhap RSS 중복 저장
- 본문 추출과 불변 저장
- 문단 ID 재현성
- Luna 선별 ID 허용 목록 검사
- Luna 관측 span 검사
- 숫자 근거 검사
- 현재 기사 간 대표 관측 쌍 생성과 기사쌍 중복 제거
- 입력 토큰 packer 상한
- Terra 사용 ID 허용 목록 검사
- 과거 ID를 쓰는 고정 fixture에서 날짜·방향·값 비교가 실제 본문에 있는지 검사
- `flows: []` 정상 처리

### PostgreSQL 통합 테스트

- 관측 vector 검색이 미래 자료를 제외하는지
- 10년 전 자료도 기간 제한 없이 후보가 되는지

---

## 25. 과거 관측 사용 여부 검증 게이트

`article_observations`가 필요하다는 가정부터 검증했다. 2026-09-01~09-10 기사를 날짜순으로 재생해 다음 두 결과를 같은 조건에서 비교했다.

```text
A안: 현재 관측 + 경제원리
B안: 현재 관측 + 이전 날짜의 관측 후보 + 경제원리
```

날짜별 기사 선별, 관측 추출, 임베딩, 원리 검색은 한 번만 실행해 A와 B가 공유한다. 과거 `daily_briefings`의 생성 문장은 두 입력 모두에서 제외한다. B가 사용하는 기억은 원문 문단과 연결된 `article_observations`뿐이다.

비교는 다음 순서로 한다.

1. 9월 1일 관측을 빈 history로 분석하고 관측만 평가 저장소에 누적한다.
2. 9월 2일은 9월 1일 관측에서 history를 찾고 A/B를 함께 생성한다.
3. 같은 방식으로 9월 10일 수집 시점까지 진행한다.
4. 연속, 방향 전환, 신규, 무관하지만 문장이 유사한 사례를 각각 확인한다.
5. 근거 오류, 변화 설명, 국가·주체 관계, 전달 경로, 초보자 효용, 추가 토큰을 날짜별로 기록한다.

B는 9월 2·4·5·8·9·10일에만 과거 값을 실제 비교에 사용했고 3·6·7일에는 사용하지 않았다. 대표 A/B 세 날짜에서는 약 837~910 입력토큰을 추가해 유가와 국채금리의 이전 값→현재 값을 명확하게 만들었고 과거를 현재 원인으로 바꾸지 않았다. 따라서 `article_observations` 과거 검색을 운영 설계에 확정한다.

검증 게이트는 통과했다. DB migration과 운영 파이프라인 교체는 Phase 0 백업·V23 상태 확인 뒤 시작할 수 있다.
- 경제원리 단수형 테이블만 조회하는지
- 최신 SUCCESS revision을 반환하는지
- 실패 run이 기존 SUCCESS 결과를 가리지 않는지

### 한 개의 실제 E2E

고정된 연합뉴스 HTML fixture와 fake OpenAI 응답으로 다음 전체 경로 하나를 검증한다.

```text
RSS 저장
→ 본문 저장
→ 관측 저장
→ 과거/원리 검색
→ 일일 브리핑 저장
→ 공개 API 응답
```

라이브 OpenAI 테스트는 기본 `test`에서 제외하고 수동 평가 명령 하나로 합친다.

---

## 26. 성공 기준

기능 성공:

- 9월 10일 오전에 9월 9일 05시부터 10일 05시 전까지의 흐름을 조회할 수 있다.
- 흐름 수는 고정하지 않으며 근거 있는 독립 흐름만 반환한다.
- 모든 사실 문장은 사용한 관측과 문단으로 추적된다.
- 과거 관측은 현재 분석창 시작보다 이전 기사에서만 온다.
- 경제원리는 실제 사건 근거와 구분된다.
- 고가형 텍스트 모델은 정상적으로 하루 한 번만 호출된다.

품질 성공:

- 상품 출시·지역 행사·단순 노사 절차 같은 기사가 흐름 중심이 되지 않는다.
- 여러 기사에서 나온 관측이 하나의 경제적 질문에 답할 때만 묶인다.
- 주장·전망·계획이 사실로 바뀌지 않는다.
- 현재 기사만으로 충분하면 과거 기록을 억지로 붙이지 않는다.
- 근거가 약한 날은 빈 결과를 허용한다.

운영 성공:

- 정상 일일 비용 월 1만 원 이내에서 시작한다.
- 한 단계 실패가 정상 기사와 이전 성공 결과를 삭제하지 않는다.
- 어떤 기사·관측·과거 기록·원리가 최종 흐름에 사용됐는지 한 run에서 확인된다.

---

## 27. 이번 설계에서 의도적으로 미룬 것

- 실시간 속보 흐름
- 일중 상태 강화·약화·반전 자동 판정
- 사용자별 맞춤 흐름
- 다중 언론사 교차 검증
- 자동 경제 그래프
- 대상 별칭 사전과 사건 병합 시스템
- HNSW/IVFFlat 인덱스
- 다중 서버 분산 잠금
- 별도 검증 LLM

일일 흐름의 품질과 비용이 검증된 뒤 실제 필요가 생긴 항목만 추가한다.

---

## 28. 저장소 정리 감사 결과

아래는 구현 시 삭제 우선순위다.

1. `delete:` 기존 Analyzer→Extractor→Validator→Judge→Deduplicator→Presenter→Stage3 체인. Luna 추출, Terra planner, Luna writer로 대체. `src/main/java/com/economicbriefing/analyzer`
2. `delete:` 사건·토픽·슬롯 그래프 전체. 짧은 관측과 vector 검색으로 대체. `src/main/java/com/economicbriefing/economicflow`
3. `delete:` Teacher와 미사용 기사 임베딩·중간 결과 저장 계층. `src/main/java/com/economicbriefing/classifier`
4. `delete:` 비활성 뉴스 소스 어댑터 9개와 관련 점수·다양성 계층. 연합뉴스 수집기 하나로 대체. `src/main/java/com/economicbriefing/collector`
5. `shrink:` `pipeline_runs`+`pipeline_logs`+`pipeline_items`와 세 결과 테이블. `daily_briefings` 한 테이블로 대체.
6. `delete:` 기사별 읽음 이력과 구형 경제망 화면. 일일 흐름 화면으로 대체. `src/main/java/com/economicbriefing/reading`, `frontend/src/components/EconomicNetwork.jsx`
7. `stdlib:` WebFlux·Spring Retry 대신 현재 사용 중인 JDK `HttpClient`와 작은 retry 함수 유지. `build.gradle.kts`
8. `delete:` 코드와 충돌하는 과거 설계·QA·브랜치 작업 문서. 최종 설계·운영·평가 문서만 유지. `docs`
9. `delete:` 루트 수동 실험 파일. 고정 fixture E2E 하나로 대체. `TestSingleArticle.java`, `test_prompt_v4.sh`

예상 순감소: 운영 Java 약 7,000~9,000줄, 테스트 약 5,000~7,000줄, 직접 의존성 5개, DB 테이블 19개 순감소(21개 제거·2개 생성).
