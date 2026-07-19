# 시스템 설계 보고서 (System Design Report)

> 최종 갱신: 2026-07-19
> 브랜치: `bingsuzoa/feature-v2-scheduler`

---

## 1. 프로젝트 개요

### 프로젝트 목적

경제 초보자(신혼부부, 사회초년생 등)가 매시간 발생하는 경제·재테크·부동산 뉴스를 이해할 수 있도록 **수집 → 분석 → 발행**을 자동화하는 프로젝트이다.

단순 요약이 아닌 다음을 설명하는 것을 목표로 한다:
- 기존에는 어땠는지
- 무엇이 바뀌었는지
- 왜 바뀌었는지
- 일반 가정에 어떤 영향이 있는지
- 예상과 확정 사실의 구분

### 전체 처리 흐름

```
[GitHub Actions Cron - 매시 정각]
        │
        ▼
[10개 RSS 소스에서 뉴스 수집]
        │
        ▼
[날짜 필터링 → 품질 검증 → 중복 제거]
        │
        ▼
[관련성 점수 → 다양성 선별]
        │
        ▼
[GPT-4o AI 분석 (시스템/사용자 프롬프트)]
        │
        ▼
[Briefing 객체 생성 및 검증]
        │
        ▼
[Notion 페이지 생성]
```

---

## 2. 시스템 아키텍처

### 전체 구성도

```
┌──────────────────────────────────────────────────────────────────┐
│                     GitHub Actions (Scheduler)                     │
│                     cron: "0 * * * *" (매시)                       │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│                    CLI Entry Point                                 │
│              src/cli/runDailyBriefingCli.ts                        │
│   - 스케줄러 옵션 파싱                                             │
│   - 환경변수 로드 및 검증                                          │
│   - Application 생성                                              │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│                   Application Orchestrator                         │
│                src/app/runDailyBriefing.ts                         │
├──────────────────────────────────────────────────────────────────┤
│  Stage 0: 중복 실행 확인 (ExecutionTracker)                        │
│  Stage 1: 뉴스 수집 (NewsCollector)                               │
│  Stage 1.5: 수집 결과 검증                                        │
│  Stage 1.7: 관련성/다양성 필터링                                   │
│  Stage 2: AI 분석 (NewsAnalyzer)                                  │
│  Stage 2.5: 분석 결과 검증                                        │
│  Stage 3: 발행 (BriefingPublisher)                                │
└───────┬──────────────────┬───────────────────┬───────────────────┘
        │                  │                   │
        ▼                  ▼                   ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────────┐
│  Collectors   │  │  Analyzers   │  │   Publishers      │
│              │  │              │  │                  │
│ RealNews     │  │ OpenAINews   │  │ NotionBriefing   │
│ Collector    │  │ Analyzer     │  │ Publisher        │
│              │  │              │  │                  │
│ 10 Source    │  │ OpenAI       │  │ NotionClient     │
│ Adapters     │  │ Client       │  │ Adapter          │
└──────────────┘  └──────────────┘  └──────────────────┘
```

### 각 모듈 역할

| 모듈 | 경로 | 역할 |
|------|------|------|
| **CLI** | `src/cli/` | 프로그램 진입점, 옵션 파싱, 실행 제어 |
| **App** | `src/app/` | 파이프라인 오케스트레이션, DI 컨테이너, 검증 |
| **Collectors** | `src/collectors/` | RSS 수집, 필터링, 정규화 |
| **Analyzers** | `src/analyzers/` | GPT-4o 기반 뉴스 분석 및 브리핑 생성 |
| **Publishers** | `src/publishers/` | Notion 페이지 생성 |
| **Domain** | `src/domain/` | 공유 타입 정의 (Zod 스키마) |
| **Config** | `src/config/` | 환경변수, 상수 |
| **Errors** | `src/errors/` | 커스텀 에러 클래스, 에러 코드 |
| **Utils** | `src/utils/` | KST 날짜 유틸리티 |
| **Scheduler** | `src/scheduler/` | 스케줄러 옵션 파싱 |
| **Pipeline** | `src/pipeline/` | DB 기반 파이프라인 잠금 및 기록 |
| **Admin** | `src/admin/` | 관리자 대시보드 (Express) |

### 모듈 간 호출 관계

```
CLI ──→ App (runDailyBriefing)
         │
         ├──→ ExecutionTracker.checkDuplicate()
         │
         ├──→ Collector.collect()
         │       ├──→ SourceAdapter[0..9].collect()
         │       │       └──→ BaseRSSAdapter → rssParser → articleNormalizer
         │       ├──→ dateFilter
         │       ├──→ qualityValidator
         │       └──→ duplicateRemover
         │
         ├──→ relevanceScorer.scoreRelevance()
         ├──→ diversitySelector.selectWithDiversity()
         │
         ├──→ Analyzer.analyze()
         │       ├──→ buildAnalysisPrompt()
         │       ├──→ OpenAIClient.complete() [retryWithBackoff]
         │       ├──→ AIResponseSchema.safeParse()
         │       └──→ buildBriefingFromAIResponse()
         │
         └──→ Publisher.publish()
                 ├──→ findPageByBriefingId() (중복 확인)
                 └──→ createBriefingPage()
```

---

## 3. 뉴스 수집

### 현재 등록된 뉴스 소스

| # | 소스명 | 어댑터 클래스 | RSS URL | 수집 대상 |
|---|--------|--------------|---------|----------|
| 1 | 연합뉴스 | `YonhapSourceAdapter` | `https://www.yna.co.kr/rss/economy.xml` | 경제 전반 |
| 2 | 한국경제 | `HankyungSourceAdapter` | `https://www.hankyung.com/feed/economy` | 경제 전반 |
| 3 | 매일경제 | `MKSourceAdapter` | `https://www.mk.co.kr/rss/30100041/` | 경제 전반 |
| 4 | SBS Biz | `SBSBizSourceAdapter` | `https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=02&plink=RSSREADER` | 경제 뉴스 |
| 5 | 서울경제 | `SedailySourceAdapter` | `https://www.sedaily.com/RSS/Economy` | 경제 전반 |
| 6 | 뉴시스 | `NewsisSourceAdapter` | `https://www.newsis.com/RSS/economy.xml` | 경제 전반 |
| 7 | 머니투데이 | `MoneyTodaySourceAdapter` | `https://rss.mt.co.kr/mt_news.xml` | 경제/금융 |
| 8 | 세계일보 | `SegyeSourceAdapter` | `https://www.segye.com/Articles/RSSList/segye_economy.xml` | 경제 |
| 9 | 경향신문 | `KhanSourceAdapter` | `https://www.khan.co.kr/rss/rssdata/economy_news.xml` | 경제 |
| 10 | 동아일보 | `DongaSourceAdapter` | `https://rss.donga.com/economy.xml` | 경제 |

### 수집 주기

- **매시 정각** (UTC 기준 `0 * * * *`)
- 직전 1시간 창(window)의 기사를 수집
- 예: KST 13:00에 실행 → 12:00:00 ~ 12:59:59 KST 게시 기사 수집

### 수집 순서

1. 10개 어댑터가 **병렬**(Promise.allSettled)로 RSS 피드 fetch
2. 각 어댑터: RSS XML 파싱 → 시간 필터링 → 카테고리 분류 → Article 정규화
3. 전체 결과 합산 후 2차 필터링 적용

### Adapter 구조

```
SourceAdapter (인터페이스)
    │
    └── BaseRSSAdapter (추상 클래스)
            │
            ├── YonhapSourceAdapter
            ├── HankyungSourceAdapter
            ├── MKSourceAdapter
            ├── SBSBizSourceAdapter
            ├── SedailySourceAdapter
            ├── NewsisSourceAdapter
            ├── MoneyTodaySourceAdapter
            ├── SegyeSourceAdapter
            ├── KhanSourceAdapter
            └── DongaSourceAdapter
```

`BaseRSSAdapter`가 제공하는 공통 기능:
- RSS 피드 HTTP 요청 (`rss-parser` 라이브러리)
- 시간 창 기반 날짜 필터링
- 키워드 기반 카테고리 분류 (`categoryClassifier`)
- Article 정규화 (`articleNormalizer`)

### 신규 소스 추가 방법

1. `src/collectors/sources/` 에 새 파일 생성
2. `BaseRSSAdapter`를 상속하여 `constructor`에서 `super(config)` 호출
3. `RealNewsCollector.ts`의 `createDefaultAdapters()`에 인스턴스 추가

```typescript
// 예시: NewSourceAdapter.ts
import { BaseRSSAdapter } from "./BaseRSSAdapter.js";

export class NewSourceAdapter extends BaseRSSAdapter {
  constructor() {
    super({
      sourceName: "새 매체명",
      sourceType: "news_media",
      feedUrls: ["https://example.com/rss/economy.xml"],
    });
  }
}
```

---

## 4. 뉴스 처리 파이프라인

### Stage 0: 중복 실행 확인

| 항목 | 내용 |
|------|------|
| **입력** | dedupeKey (날짜+시간 조합, 예: `2026-07-19T12`) |
| **출력** | `PublicationDecision`: publish / skip_already_published / retry_previous_failure |
| **사용 클래스** | `ExecutionTracker` (인터페이스), `MockExecutionTracker` (구현) |
| **알고리즘** | 같은 시간 창에 이미 성공한 실행이 있으면 skip |

### Stage 1: RSS 수집

| 항목 | 내용 |
|------|------|
| **입력** | `CollectNewsRequest` (targetDate, timezone, startTime, endTime) |
| **출력** | `CollectNewsResult` (articles[], sourceReports[], totalCollected, totalAccepted, totalRejected) |
| **사용 클래스** | `RealNewsCollector`, `BaseRSSAdapter` × 10 |
| **알고리즘** | Promise.allSettled 병렬 수집, 개별 실패 허용 |

### Stage 1.5: 수집 후 필터링

```
RSS 원본 기사들
    │
    ├── [날짜 필터] filterByDate()
    │     - 시간 창 밖의 기사 제거
    │
    ├── [품질 검증] validateQuality()
    │     - 제목 5자 미만 제거
    │     - URL 유효성 검사
    │     - 날짜 파싱 불가 제거
    │     - 광고 키워드 포함 제거 ([AD], [광고], [제휴] 등)
    │
    └── [중복 제거] removeDuplicates()
          - Phase 1: URL 정규화 비교 (추적 파라미터 제거, 모바일 URL 통합)
          - Phase 1: 동일 소스 제목 유사도 > 80% 제거
          - Phase 2: 교차 소스 이벤트 그룹핑 (제목 유사도 > 80%)
          - 그룹 내 대표 기사 선정 (품질 점수 기반)
```

| 항목 | 내용 |
|------|------|
| **입력** | 전체 수집 기사 배열 |
| **출력** | 필터링된 유효 기사 배열 |
| **사용 클래스** | `dateFilter`, `qualityValidator`, `duplicateRemover` |
| **알고리즘** | URL 정규화, 문자 유사도 비교 (character overlap ratio), 품질 점수 기반 대표 선정 |

### Stage 1.7: 관련성 및 다양성 선별

```
유효 기사들
    │
    ├── [관련성 점수] scoreRelevance()
    │     - 키워드 매칭으로 0~5점 부여
    │     - 최소 점수(3점) 미만 제외
    │
    └── [다양성 선별] selectWithDiversity()
          - 관련성 점수 내림차순 정렬
          - 소스별 최대 3개 (hard cap)
          - 카테고리별 최대 3개 (soft cap, 5점 기사는 +1 허용)
```

| 항목 | 내용 |
|------|------|
| **입력** | 필터링된 기사 배열 |
| **출력** | AI 분석 대상 기사 배열 |
| **사용 클래스** | `relevanceScorer`, `diversitySelector` |
| **알고리즘** | 5단계 키워드 그룹 (점수 5→1), 소스/카테고리 다양성 캡핑 |

**관련성 점수 기준:**
- 5점: 가계에 직접 영향 (금리 변동, 대출 규제, 세율 변경)
- 4점: 주요 재무 영향 (집값, 연금, 예적금 금리)
- 3점: 간접 영향 (물가, 환율, ETF, 공공요금)
- 2점: 경제 카테고리 기사 (기본 점수)
- 1점: 기업 뉴스, 인사, M&A

### Stage 2: AI 분석

| 항목 | 내용 |
|------|------|
| **입력** | `AnalyzeNewsRequest` (articles, targetDate, maxSelectedNews, audience) |
| **출력** | `AnalyzeNewsResult` (briefing, rejectedArticleIds, warnings) |
| **사용 클래스** | `OpenAINewsAnalyzer`, `OpenAIClient`, `retryWithBackoff` |
| **알고리즘** | GPT-4o JSON 응답 → Zod 스키마 검증 → Briefing 객체 조립 |

### Stage 2.5: 분석 결과 검증

| 항목 | 내용 |
|------|------|
| **입력** | `AnalyzeNewsResult` |
| **출력** | 유효성 판정 + 경고 목록 |
| **사용 클래스** | `validateAnalyzeResult` (validatePipelineData.ts) |
| **알고리즘** | Briefing 스키마 재검증, 필수 필드 확인 |

### Stage 3: Notion 게시

| 항목 | 내용 |
|------|------|
| **입력** | `PublishBriefingRequest` (briefing, dryRun) |
| **출력** | `PublishBriefingResult` (results[], completedAt) |
| **사용 클래스** | `NotionBriefingPublisher`, `NotionClientAdapter`, `buildNotionBriefingBlocks` |
| **알고리즘** | 중복 확인 → 페이지 생성 → 100블록 단위 분할 추가 |

---

## 5. AI 분석

### GPT 호출 위치

`src/analyzers/openai/OpenAINewsAnalyzer.ts` → `OpenAIClient.complete()`

### 사용 모델

- **기본 모델:** `gpt-4o`
- 생성자 옵션으로 변경 가능

### Prompt 종류

| Prompt | 파일 | 역할 |
|--------|------|------|
| System Prompt | `src/analyzers/openai/prompts/systemPrompt.ts` | AI 역할, 분석 원칙, 출력 형식 정의 |
| User Prompt | `src/analyzers/openai/prompts/buildAnalysisPrompt.ts` | 기사 목록, 대상 독자, 요청사항 전달 |

### 입력

- System Prompt: 고정된 분석 지침 (역할, 원칙, JSON 출력 형식)
- User Prompt: 동적으로 생성
  - 대상 날짜
  - 최대 선별 뉴스 수 (기본 10)
  - 대상 독자 프로필 (초보자, 관심 분야, 참고 사항)
  - 기사 목록 (ID, 제목, 요약, 출처, URL, 카테고리, 본문)

### 출력

JSON 형식으로 `AIResponseSchema` (Zod)로 검증:
```json
{
  "overallSummary": ["요약 문장"],
  "news": [{
    "id": "news-1",
    "representativeTitle": "제목",
    "category": "카테고리 코드",
    "importance": 1-5,
    "whyImportant": "왜 중요한가",
    "oneLineSummary": "한 줄 결론",
    "explanation": "[무슨 일이 있었나]...[왜 이런 일이 발생했나]...[우리에게 어떤 의미가 있나]...",
    "evidenceStatus": "confirmed | proposed | expected",
    "uncertaintyNote": "(선택)",
    "economicTerms": [{"term": "", "explanation": "", "example": ""}],
    "sources": [{"articleId": "", "isPrimary": true}]
  }],
  "glossary": [{"term": "", "explanation": ""}]
}
```

### Temperature

- **0.3** (낮은 창의성, 높은 일관성)

### Response Format

- `{ type: "json_object" }` — OpenAI JSON mode 강제

### Retry

| 설정 | 값 |
|------|---|
| 최대 시도 횟수 | 2 |
| 초기 대기 시간 | 1,000ms |
| 이후 대기 시간 | 2,000ms |
| 재시도 조건 | `AppError.retryable === true` |
| 재시도 가능 에러 | 429 (Rate Limit), 500, 503, Connection Error, JSON 파싱 실패, 스키마 검증 실패 |

### Timeout

- **AI API:** 60,000ms (60초)

---

## 6. 브리핑 생성

### Notion 페이지 구조

`buildNotionBriefingBlocks()` (src/publishers/notion/buildNotionPage.ts)가 생성하는 블록 구조:

```
## 오늘의 핵심 요약
  • 요약 문장 1
  • 요약 문장 2

## 주요 뉴스
  ───────────────────
  ### 뉴스 제목 (중요도 N/5)
  한 줄 결론: ...
  왜 중요한가: ...

  ### 무슨 일이 있었나
  본문...

  ### 왜 이런 일이 발생했나
  본문...

  ### 우리에게 어떤 의미가 있나
  본문...

  ### 뉴스 안의 경제용어
  • 용어: 설명

  ### 출처
  • 대표 출처: 매체명 - 제목 (날짜) [링크]

  (뉴스 반복...)

## 경제용어
  • 용어: 설명 예: 예시

## 브리핑 정보
  생성 시각: ...
  수집 기사: N개
  분석 기사: N개
  선택 뉴스: N개
  AI 모델: gpt-4o
  프롬프트 버전: v1
```

### 각 항목의 생성 방식

| 항목 | 소스 필드 | Notion 블록 타입 |
|------|----------|-----------------|
| 오늘의 핵심 요약 | `briefing.overallSummary[]` | heading_2 + bulleted_list_item |
| 뉴스 제목 | `news.representativeTitle` + `news.importance` | heading_3 |
| 한 줄 결론 | `news.oneLineSummary` | paragraph |
| 왜 중요한가 | `news.whyImportant` | paragraph |
| 해설 본문 | `news.explanation` (줄바꿈으로 분할) | heading_3 (소제목) + paragraph |
| 경제용어 | `news.economicTerms[]` | bulleted_list_item |
| 출처 | `news.sources[]` | bulleted_list_item (URL 링크 포함) |
| 브리핑 정보 | `briefing.metadata` | paragraph |

텍스트 길이 제한: Notion API의 rich_text 최대 2,000자로 자동 분할 (`splitText` 함수).

---

## 7. Scheduler

### Scheduler 종류

GitHub Actions workflow cron (서버리스)

### Cron 표현식

```
0 * * * *
```

### 실행 시간

- UTC 기준 매시 정각 (00분)
- KST 기준 매시 정각 (UTC+9)
- 24시간 × 365일 상시 실행

### 타임존

- 워크플로우 환경: `TZ=Asia/Seoul`
- 내부 시간 처리: KST (UTC+9) 기준
- 시간 창 계산: 실행 시점 기준 직전 1시간

### 실행 순서 (workflow)

1. Checkout 소스 코드
2. Node.js 20 설정
3. `npm ci` (의존성 설치)
4. `npm run typecheck` (타입 검사)
5. `npm run lint` (린트)
6. `npm test` (테스트)
7. `npm run build` (빌드)
8. `npm start` (파이프라인 실행)

### 재시도 정책

- GitHub Actions 자체 재시도: **없음**
- AI API 호출 재시도: 최대 2회 (exponential backoff)
- RSS 수집: 개별 소스 실패 허용 (다른 소스 계속 진행)
- Workflow timeout: 30분

### 중복 실행 방지

```yaml
concurrency:
  group: hourly-briefing
  cancel-in-progress: false
```

- 같은 concurrency group에서 동시 실행 방지
- `cancel-in-progress: false` → 먼저 실행 중인 작업 보호
- 애플리케이션 레벨: `ExecutionTracker`로 같은 시간 창 재처리 방지
- Notion 레벨: 같은 `briefingId`로 생성된 페이지가 있으면 skip

---

## 8. Notion

### 게시 과정

```
1. DRY_RUN 확인 → true이면 스킵 (externalId: "dry-run:{id}")
2. findPageByBriefingId() → 기존 페이지 존재 확인
3. 기존 페이지 있으면 → "skipped" (PUBLISH_DUPLICATE)
4. buildNotionBriefingBlocks() → Notion 블록 배열 생성
5. createBriefingPage() → 페이지 생성 (100블록 단위 분할)
```

### 페이지 생성 방식

- **데이터베이스:** `NOTION_DATABASE_ID` 환경변수로 지정
- **Data Source Resolution:** database_id → data_source_id 자동 변환
- **Properties 매핑:**

| Notion Property | 값 |
|-----------------|---|
| Name (title) | `briefing.title` (예: "2026-07-19 12시 경제 브리핑") |
| Briefing ID (rich_text) | `briefing.id` |
| Target Date (date) | `briefing.targetDate` |
| Generated At (date) | `briefing.generatedAt` |
| News Count (number) | `briefing.news.length` |

- **블록 분할:** Notion API 제한(100 children/request)에 따라 자동 청크 분할
- 첫 100블록은 페이지 생성 시 포함, 나머지는 `blocks.children.append()`로 추가

### 데이터 매핑

```
Briefing
  ├── id → Notion "Briefing ID" property
  ├── title → Notion "Name" property
  ├── targetDate → Notion "Target Date" property
  ├── generatedAt → Notion "Generated At" property
  ├── overallSummary → heading_2 + bulleted_list_items
  ├── news[] → 반복: divider + heading_3 + paragraphs
  ├── glossary → heading_2 + bulleted_list_items
  └── metadata → heading_2 + paragraphs
```

---

## 9. Email

### 현재 상태

Email 발행은 **미구현** 상태이다.

- 환경변수(`EMAIL_PROVIDER`, `EMAIL_FROM`, `EMAIL_TO`, `EMAIL_API_KEY`)는 정의되어 있으나 모두 optional
- `PublishChannelResult`의 channel에 `"email"` enum 값이 존재
- 실제 `EmailPublisher` 구현체는 존재하지 않음
- `BriefingPublisher` 인터페이스와 `MockBriefingPublisher`만 존재

### 실패 처리

해당 없음 (미구현).

---

## 10. 환경 변수

| 변수명 | 필수 | 기본값 | 용도 |
|--------|------|--------|------|
| `NODE_ENV` | X | `development` | 실행 환경 (development/production/test) |
| `TZ` | X | `Asia/Seoul` | 시스템 타임존 |
| `DRY_RUN` | X | `true` | 외부 채널 저장 없이 테스트 실행 여부 |
| `LOG_LEVEL` | X | `info` | 로그 수준 (debug/info/warn/error) |
| `OPENAI_API_KEY` | O* | - | OpenAI API 인증 키 |
| `NOTION_API_KEY` | O* | - | Notion Integration 토큰 |
| `NOTION_DATABASE_ID` | O* | - | 브리핑 저장 대상 Notion 데이터베이스 ID |
| `EMAIL_PROVIDER` | X | - | (미사용) 이메일 프로바이더 |
| `EMAIL_FROM` | X | - | (미사용) 발신자 주소 |
| `EMAIL_TO` | X | - | (미사용) 수신자 주소 |
| `EMAIL_API_KEY` | X | - | (미사용) 이메일 API 키 |
| `DATABASE_URL` | X | - | PostgreSQL 연결 문자열 (admin 대시보드용) |
| `DB_HOST` | X | `localhost` | DB 호스트 |
| `DB_PORT` | X | `5432` | DB 포트 |
| `DB_NAME` | X | `economic_briefing` | DB 이름 |
| `DB_USER` | X | `postgres` | DB 사용자 |
| `DB_PASSWORD` | X | `""` | DB 비밀번호 |
| `ADMIN_TOKEN` | X | `""` | 관리자 대시보드 인증 토큰 |
| `ADMIN_PORT` | X | `3000` | 관리자 대시보드 포트 |
| `TARGET_DATE` | X | - | 수동 실행 시 대상 날짜 (YYYY-MM-DD) |
| `GITHUB_EVENT_NAME` | X | - | GitHub Actions 이벤트 타입 감지 |

*O*: 프로덕션 실행 시 필수 (DRY_RUN=false일 때)

---

## 11. 폴더 구조

```
feature-v2-scheduler/
├── src/
│   ├── admin/              # 관리자 대시보드 (Express REST API + 정적 UI)
│   │   ├── middleware/     # 인증, 에러 핸들링 미들웨어
│   │   ├── routes/         # API 라우트 (runs, items, status)
│   │   └── public/         # 정적 CSS/JS 에셋
│   ├── analyzers/          # AI 분석 모듈
│   │   ├── openai/         # OpenAI 구현
│   │   │   ├── prompts/    # 시스템/사용자 프롬프트, 응답 스키마
│   │   │   └── utils/      # 재시도, 브리핑 조립 유틸
│   │   └── mock/           # 테스트용 Mock 구현
│   ├── app/                # 애플리케이션 오케스트레이션
│   ├── cli/                # CLI 진입점
│   ├── collectors/         # 뉴스 수집 모듈
│   │   ├── sources/        # 10개 소스 어댑터 + 베이스 클래스
│   │   ├── filters/        # 6개 필터 (날짜, 품질, 중복, 카테고리, 관련성, 다양성)
│   │   ├── parsers/        # RSS 파싱, 기사 정규화
│   │   └── mock/           # 테스트용 Mock 구현
│   ├── config/             # 환경변수 스키마, 상수
│   ├── db/                 # DB 연결, 마이그레이션, 리포지토리
│   │   ├── repositories/   # PipelineRun/Log/Item 리포지토리
│   │   └── mock/           # Mock 리포지토리
│   ├── domain/             # 도메인 타입 (Zod 스키마)
│   ├── errors/             # 커스텀 에러 클래스, 에러 코드
│   ├── pipeline/           # 파이프라인 잠금, 기록
│   ├── publishers/         # 발행 모듈
│   │   ├── notion/         # Notion 구현
│   │   └── mock/           # 테스트용 Mock 구현
│   ├── scheduler/          # 스케줄러 옵션 파싱
│   └── utils/              # 날짜 유틸리티
├── tests/                  # 미러 구조 테스트 (Vitest)
├── migrations/             # PostgreSQL 마이그레이션 SQL
├── scripts/                # 수동 실행 스크립트
├── docs/                   # 프로젝트 문서
└── .github/workflows/      # CI/CD 워크플로우
```

---

## 12. 데이터 모델

### Article (수집된 기사)

```typescript
{
  id: string;                    // 고유 식별자
  title: string;                 // 기사 제목
  summary: string;               // 요약
  sourceName: string;            // 매체명 (예: "연합뉴스")
  sourceType: ArticleSourceType; // "news_media" | "government" | ...
  publishedAt: ISODateTime;      // 게시 시각 (KST)
  collectedAt: ISODateTime;      // 수집 시각 (KST)
  url: UrlString;                // 원문 URL
  categories: NewsCategory[];    // 카테고리 (16종)
  language: "ko";                // 언어 (고정)
  content?: string;              // 본문 (선택)
}
```

### AnalyzedNews (AI 분석 결과)

```typescript
{
  id: string;                    // 뉴스 식별자 (예: "news-1")
  representativeTitle: string;   // 대표 제목
  category: NewsCategory;        // 주요 카테고리
  importance: 1 | 2 | 3 | 4 | 5; // 중요도
  whyImportant: string;          // 왜 중요한가
  targetAudience?: TargetAudience; // (선택, 현재 미생성)
  impactAssessment?: ImpactScore[]; // (선택, 현재 미생성)
  oneLineSummary: string;        // 한 줄 결론
  explanation: string;           // 3단계 해설
  evidenceStatus: "confirmed" | "proposed" | "expected";
  uncertaintyNote?: string;      // 불확실성 안내
  economicTerms: EconomicTerm[]; // 경제 용어 설명
  sources: SourceReference[];    // 출처 기사 참조
}
```

### Briefing (최종 브리핑)

```typescript
{
  id: string;                    // 브리핑 식별자
  targetDate: ISODate;           // 대상 날짜
  generatedAt: ISODateTime;      // 생성 시각
  title: string;                 // 브리핑 제목
  overallSummary: string[];      // 전체 요약
  news: AnalyzedNews[];          // 분석된 뉴스 목록
  glossary: EconomicTerm[];      // 경제 용어 모음
  metadata: BriefingMetadata;    // 메타 정보
}
```

### 객체 흐름

```
[RSS XML] → rssParser → [RSSItem]
                              │
                              ▼
                     articleNormalizer → [Article]
                                            │
                    ┌───────────────────────┘
                    ▼
         [Article[]] → filters → [Article[]] (필터링됨)
                                      │
                                      ▼
                           OpenAINewsAnalyzer.analyze()
                                      │
                                      ▼
                              [AIResponse] (JSON)
                                      │
                                      ▼
                        buildBriefingFromAIResponse()
                                      │
                                      ▼
                               [Briefing]
                                      │
                                      ▼
                        buildNotionBriefingBlocks()
                                      │
                                      ▼
                            [NotionBlock[]]
                                      │
                                      ▼
                         NotionClientAdapter.createPage()
                                      │
                                      ▼
                              [Notion Page]
```

---

## 13. 예외 처리

### 에러 코드 체계

| 스테이지 | 에러 코드 | 설명 | 재시도 가능 |
|---------|----------|------|-----------|
| collect | `COLLECT_SOURCE_TIMEOUT` | RSS 소스 타임아웃 | O |
| collect | `COLLECT_SOURCE_UNAVAILABLE` | RSS 소스 접근 불가 | O |
| collect | `COLLECT_PARSE_ERROR` | RSS 파싱 실패 | X |
| collect | `COLLECT_NO_ARTICLES` | 유효 기사 없음 | X |
| analyze | `ANALYZE_API_ERROR` | OpenAI API 에러 | 조건부* |
| analyze | `ANALYZE_VALIDATION_ERROR` | AI 응답 검증 실패 | O |
| analyze | `ANALYZE_TIMEOUT` | AI API 타임아웃 | O |
| analyze | `ANALYZE_EMPTY_INPUT` | 분석 대상 기사 없음 | X |
| publish | `PUBLISH_CHANNEL_ERROR` | 채널 발행 실패 | 조건부** |
| publish | `PUBLISH_DUPLICATE` | 이미 발행됨 | X |
| system | `SYSTEM_CONFIG_ERROR` | 설정 오류 | X |
| system | `SYSTEM_UNEXPECTED` | 예상치 못한 오류 | X |

*429, 500, 503 → 재시도 가능
**Rate Limit, Internal Error, Service Unavailable, Request Timeout → 재시도 가능

### RSS 실패 처리

- `Promise.allSettled()` 사용으로 개별 소스 실패가 전체 수집을 중단시키지 않음
- 실패한 소스는 `sourceReports`에 `status: "failed"`로 기록
- 모든 소스가 실패해도 빈 배열이 반환되며 "0 articles collected"로 정상 종료

### AI 실패 처리

- `retryWithBackoff`: 재시도 가능한 에러에 대해 최대 2회 시도
- JSON 파싱 실패: 재시도 가능 (AI가 가끔 잘못된 JSON 생성)
- 스키마 검증 실패: 재시도 가능
- 최종 실패 시: `ExecutionLog.status = "failed"`, 에러 기록 후 종료

### Notion 실패 처리

- 중복 페이지 감지 시: `"skipped"` (에러가 아닌 정상 스킵)
- API 에러 발생 시: `PublishChannelResult.status = "failed"` 기록
- 재시도 가능 에러: Rate Limit, Internal Server Error, Service Unavailable, Request Timeout
- 현재 Notion 레벨 재시도 로직은 미구현 (에러 기록만)

### Email 실패 처리

해당 없음 (미구현).

### 전체 파이프라인 상태 결정

```
모든 채널 성공/스킵 → "success"
일부 채널 성공     → "partial_success"
모든 채널 실패     → "failed"
수집/분석 실패     → "failed" (조기 종료)
```

---

## 14. 현재 한계

### 기술 부채 및 문제점

| 우선순위 | 항목 | 설명 |
|---------|------|------|
| **높음** | RSS 의존성 | 모든 소스가 RSS에 의존하며, RSS 피드 변경/중단 시 즉시 수집 불가 |
| **높음** | 재시도 미흡 | Notion 발행 실패 시 애플리케이션 레벨 재시도 없음 (기록만) |
| **높음** | 모니터링 부재 | 실패 알림 수단 없음 (Slack, Email 등 미연동) |
| **중간** | Email 미구현 | 인터페이스만 정의되어 있고 실제 이메일 발송 기능 없음 |
| **중간** | 수집 범위 한계 | 한국 경제 매체 10곳으로 제한, 정부/공공기관 RSS 미작동 |
| **중간** | ExecutionTracker 영속성 | `MockExecutionTracker`는 메모리 기반으로 프로세스 재시작 시 기록 소실 |
| **중간** | DB 미연동 | Pipeline 실행 기록이 DB에 저장되지 않음 (admin dashboard DB 연동 필요) |
| **낮음** | 본문 수집 미흡 | RSS는 제목+요약만 제공하는 경우가 많아 AI 분석 품질에 한계 |
| **낮음** | 카테고리 분류 정확도 | 키워드 기반 분류는 문맥 이해 불가, 미분류 기사 발생 |
| **낮음** | 테스트 커버리지 | 통합 테스트는 Mock 기반으로만 존재, 실제 API 연동 테스트 부재 |
| **낮음** | 야간 시간대 | 밤 시간(23시~06시) 수집 시 기사가 거의 없어 빈 실행 반복 |

### 향후 개선 방향

1. **정부 기관 데이터 수집** — RSS가 작동하지 않으므로 웹 스크래핑 또는 API 대안 필요
2. **실행 기록 영속화** — PostgreSQL 기반 ExecutionTracker 구현
3. **알림 시스템** — 실패 시 Slack/Email 알림
4. **Email 발행** — 구독자 대상 이메일 브리핑
5. **본문 크롤링** — RSS 본문이 부족한 소스에 대해 웹 페이지 본문 추출
6. **비활성 시간대 스킵** — 새벽 시간대 불필요 실행 방지

---

## 15. 전체 실행 순서

### Sequence Diagram

```
┌──────────┐  ┌──────────┐  ┌──────────────┐  ┌──────────┐  ┌──────────┐  ┌───────┐
│ GitHub   │  │   CLI    │  │  Collector   │  │ Analyzer │  │Publisher │  │ Notion│
│ Actions  │  │          │  │              │  │          │  │          │  │  API  │
└────┬─────┘  └────┬─────┘  └──────┬───────┘  └────┬─────┘  └────┬─────┘  └───┬───┘
     │              │               │               │              │            │
     │ npm start    │               │               │              │            │
     │─────────────>│               │               │              │            │
     │              │               │               │              │            │
     │              │ parseOptions() │              │              │            │
     │              │───┐            │               │              │            │
     │              │<──┘            │               │              │            │
     │              │               │               │              │            │
     │              │ loadEnv()     │               │              │            │
     │              │───┐            │               │              │            │
     │              │<──┘            │               │              │            │
     │              │               │               │              │            │
     │              │ getHourlyTimeRange()          │              │            │
     │              │───┐            │               │              │            │
     │              │<──┘ (12:00~12:59)             │              │            │
     │              │               │               │              │            │
     │              │ checkDuplicate(dedupeKey)     │              │            │
     │              │───┐            │               │              │            │
     │              │<──┘ "publish"  │               │              │            │
     │              │               │               │              │            │
     │              │ collect()     │               │              │            │
     │              │──────────────>│               │              │            │
     │              │               │               │              │            │
     │              │               │ [10 adapters parallel]       │            │
     │              │               │──┐ fetchRSS() × 10          │            │
     │              │               │<─┘             │              │            │
     │              │               │               │              │            │
     │              │               │ filterByDate() │             │            │
     │              │               │──┐             │              │            │
     │              │               │<─┘             │              │            │
     │              │               │               │              │            │
     │              │               │ validateQuality()            │            │
     │              │               │──┐             │              │            │
     │              │               │<─┘             │              │            │
     │              │               │               │              │            │
     │              │               │ removeDuplicates()           │            │
     │              │               │──┐             │              │            │
     │              │               │<─┘             │              │            │
     │              │               │               │              │            │
     │              │  CollectNewsResult            │              │            │
     │              │<──────────────│               │              │            │
     │              │               │               │              │            │
     │              │ validateCollectResult()       │              │            │
     │              │───┐            │               │              │            │
     │              │<──┘            │               │              │            │
     │              │               │               │              │            │
     │              │ scoreRelevance()             │              │            │
     │              │───┐            │               │              │            │
     │              │<──┘            │               │              │            │
     │              │               │               │              │            │
     │              │ selectWithDiversity()         │              │            │
     │              │───┐            │               │              │            │
     │              │<──┘            │               │              │            │
     │              │               │               │              │            │
     │              │ analyze()     │               │              │            │
     │              │──────────────────────────────>│              │            │
     │              │               │               │              │            │
     │              │               │               │ buildAnalysisPrompt()     │
     │              │               │               │──┐           │            │
     │              │               │               │<─┘           │            │
     │              │               │               │              │            │
     │              │               │               │ retryWithBackoff {        │
     │              │               │               │   OpenAI.chat.completions.create()
     │              │               │               │──────────────────────────>│
     │              │               │               │              │            │
     │              │               │               │   JSON response           │
     │              │               │               │<─────────────────────────│
     │              │               │               │              │            │
     │              │               │               │   AIResponseSchema.parse()│
     │              │               │               │──┐           │            │
     │              │               │               │<─┘           │            │
     │              │               │               │ }            │            │
     │              │               │               │              │            │
     │              │               │               │ buildBriefingFromAIResponse()
     │              │               │               │──┐           │            │
     │              │               │               │<─┘           │            │
     │              │               │               │              │            │
     │              │  AnalyzeNewsResult            │              │            │
     │              │<─────────────────────────────│              │            │
     │              │               │               │              │            │
     │              │ validateAnalyzeResult()       │              │            │
     │              │───┐            │               │              │            │
     │              │<──┘            │               │              │            │
     │              │               │               │              │            │
     │              │ publish()     │               │              │            │
     │              │─────────────────────────────────────────────>│            │
     │              │               │               │              │            │
     │              │               │               │              │ findPage() │
     │              │               │               │              │───────────>│
     │              │               │               │              │<───────────│
     │              │               │               │              │            │
     │              │               │               │              │ createPage()
     │              │               │               │              │───────────>│
     │              │               │               │              │<───────────│
     │              │               │               │              │            │
     │              │  PublishBriefingResult        │              │            │
     │              │<────────────────────────────────────────────│            │
     │              │               │               │              │            │
     │              │ recordExecution()             │              │            │
     │              │───┐            │               │              │            │
     │              │<──┘            │               │              │            │
     │              │               │               │              │            │
     │  exit(0)     │               │               │              │            │
     │<─────────────│               │               │              │            │
     │              │               │               │              │            │
```

### 정상 종료 조건

- 모든 스테이지 통과 + Notion 페이지 생성 성공 → exit code 0
- 수집 기사 0건 (해당 시간대에 뉴스 없음) → exit code 0 (정상)
- 이미 발행된 시간 창 → exit code 0 (skip)

### 비정상 종료 조건

- 수집 후 유효 기사 0건 (모두 필터 탈락) → exit code 1
- AI 분석 실패 (재시도 소진) → exit code 1
- Notion 발행 실패 → exit code 1
- 환경변수 검증 실패 → exit code 1

---

## 부록: 기술 스택

| 분류 | 기술 | 버전 |
|------|------|------|
| Runtime | Node.js | 20+ |
| Language | TypeScript | 5.5+ (strict) |
| Module | ESM | ES2022 |
| Validation | Zod | 3.23 |
| AI | OpenAI SDK | 6.47 |
| CMS | Notion SDK | 5.23 |
| RSS | rss-parser | 3.13 |
| Web Framework | Express | 5.2 |
| Database | PostgreSQL (pg) | 8.22 |
| Testing | Vitest | - |
| Linting | ESLint | Flat config |
| CI/CD | GitHub Actions | - |
