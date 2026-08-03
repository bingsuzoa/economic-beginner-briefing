# 뉴스 수집 및 AI 분석 파이프라인

## 개요

경제 뉴스 브리핑 자동화 시스템의 전체 파이프라인 구조

```
수집 → 필터링 → 검증 → AI 분석(2단계) → 필터링 → 저장 → API 제공
```

---

## 1. 뉴스 수집 (Collection)

### 1.1 소스 어댑터
**위치:** `collector/source/`

#### 지원 언론사 (11개)
- **연합뉴스** (YonhapSourceAdapter) - 6개 RSS 피드
  - economy.xml (경제)
  - politics.xml (정치)
  - society.xml (사회)
  - international.xml (국제)
  - industry.xml (산업)
  - culture.xml (문화)
- 동아일보 (DongaSourceAdapter)
- 한국경제 (HankyungSourceAdapter)
- 경향신문 (KhanSourceAdapter)
- 매일경제 (MKSourceAdapter)
- 머니투데이 (MoneyTodaySourceAdapter)
- 뉴시스 (NewsisSourceAdapter)
- SBS Biz (SBSBizSourceAdapter)
- 서울경제 (SedailySourceAdapter)
- 세계일보 (SegyeSourceAdapter)

### 1.2 수집 프로세스
**담당:** `DefaultNewsCollector`

```java
1. 병렬 수집 (ExecutorService, 10 threads)
   - 모든 소스 어댑터를 병렬로 실행
   - CompletableFuture로 비동기 처리
   - RSS 파싱 → RssItem 리스트 생성

2. 시간 필터링
   - publishedDate가 startTime ~ endTime 범위 내만 수집
   - 기본값: targetDate 00:00 ~ 23:59:59 (KST)

3. 품질 검증 (QualityValidator)
   - 제목 null/empty 체크
   - URL null/empty 체크
   - 발행일 null 체크

4. 중복 제거 (DuplicateRemover)
   - URL 기반 중복 제거
   - 제목 유사도 체크

5. 개수 제한
   - maxArticles 파라미터로 제한 (설정 가능)
```

### 1.3 Article 정규화
**담당:** `ArticleNormalizer`

```java
- HTML 태그 제거 (description, content)
- ID 생성: Hash(url + publishedDate)
- 제목/요약 정리
- 카테고리: 초기에는 OTHER (AI가 재분류)
```

**출력:** `CollectNewsResult`
- articles: List<Article>
- totalCollected: int (RSS에서 가져온 총 개수)
- acceptedCount: int (필터링 후 개수)

---

## 2. 파이프라인 검증

### 2.1 수집 결과 검증
**담당:** `PipelineDataValidator.validateCollectResult()`

```java
검증 항목:
- targetDate 일치 확인
- Article 필수 필드 체크 (id, title, url)
- 경고 로그 생성
```

### 2.2 Article 저장
**담당:** `ArticlePersistenceService`

```
DB 테이블: articles
- article_id (PK)
- title, url, source, published_at
- collected_at, categories
```

---

## 3. AI 분석 (2단계 파이프라인)

### 3.1 단계 1: 선별 (Selection)
**담당:** `OpenAiNewsAnalyzer.analyze()` - Stage 1

**프롬프트:** `SelectionPromptBuilder`

```
입력:
- 100개 기사 (제목만)
- 최대 선별 개수 (예: 15개)
- 독자 프로필

출력:
{
  "selectedArticleIds": ["id1", "id2", ...]
}
```

**선별 기준:**
- 대상 독자의 경제생활에 직접 영향
- 주요 경제 정책 변화
- **주요 기업 실적 발표, 종목 분석, 산업 뉴스**
- 부동산, 금리, 환율 등 자산 가격 변동

**제외 기준:**
- 기업 인사, 외교 의전, 정치 스캔들
- 날씨, 스포츠, 연예
- 사건사고, 범죄, 재난
- 지역 소식

**API 호출:** 1번
- 모델: GPT-4o
- 입력 토큰: ~2.5K (제목만)
- 출력 토큰: ~200 (ID 리스트)

### 3.2 단계 2: 분석 (Analysis)
**담당:** `OpenAiNewsAnalyzer.analyze()` - Stage 2

**프롬프트:** `AnalysisPromptBuilder` + `SystemPromptBuilder`

```
입력:
- 선별된 15개 기사 (제목 + 요약)
- 순서 보장

출력:
{
  "overallSummary": ["요약1", "요약2"],
  "news": [
    {
      "id": "news-1",
      "easyTitle": "초보자용 제목",
      "category": "interest_rate",
      "importance": 5,
      "threeLineSummary": ["핵심1", "핵심2", "핵심3"],
      "whatHappened": "사건 요약",
      "whyItHappened": "원인",
      "beginnerExplanation": "경제 원리 설명",
      "economicImpact": "경제 영향",
      "terms": [{"term": "용어", "explanation": "설명"}],
      "evidenceStatus": "confirmed"
    }
  ],
  "glossary": [...]
}
```

**주요 원칙:**
- 사실과 주장 구분
- 발화자 명시 (주장의 경우)
- 기사에 없는 내용 추측 금지
- 초보자가 이해할 수 있도록 설명

**API 호출:** 1번
- 모델: GPT-4o
- 입력 토큰: ~4K (선별된 기사만)
- 출력 토큰: ~6K (분석 결과)

### 3.3 순서 기반 매핑
**담당:** `BriefingBuilder`

```java
// AI 응답의 순서와 입력 기사의 순서가 일치
news[0] → selectedArticles[0]
news[1] → selectedArticles[1]
...

// SourceReference 자동 생성
SourceReference source = new SourceReference(
    article.id(),
    article.sourceName(),
    article.title(),
    article.url(),
    article.publishedAt(),
    true  // isPrimary
);
```

**매핑 오류 원천 차단:**
- AI가 articleId를 선택하지 않음
- 시스템이 순서로 자동 매핑
- 잘못된 ID 반환 불가능

---

## 4. 분석 결과 검증 및 필터링

### 4.1 분석 결과 검증
**담당:** `PipelineDataValidator.validateAnalyzeResult()`

```java
검증 항목:
- news 배열 비어있지 않음
- 각 뉴스에 sources 존재
- source에 URL 존재
```

### 4.2 무관한 뉴스 필터링
**담당:** `BriefingPipeline.filterIrrelevantNews()`

```java
필터 기준:
1. category=OTHER && importance≤1 제외
2. economicImpact에 "경제적 영향과는 관련이 없" 포함 시 제외
3. economicImpact에 "경제적 영향은 없" 포함 시 제외
```

---

## 5. 저장 및 API 제공

### 5.1 DB 저장
**담당:** `BriefingPipeline.saveArticleAnalyses()`

```sql
테이블: article_analyses
- article_id (PK)
- analysis_json (JSONB) - 전체 분석 결과
- model_name, prompt_version
- created_at
```

### 5.2 API 제공
**엔드포인트:** `/api/briefing/articles`

**응답 구조:**
```json
[
  {
    "articleId": "abc123",
    "easyTitle": "초보자용 제목",
    "category": "interest_rate",
    "importance": 5,
    "threeLineSummary": [...],
    "whatHappened": "...",
    "whyItHappened": "...",
    "beginnerExplanation": "...",
    "economicImpact": "...",
    "terms": [...],
    "sources": [...],
    "originalTitle": "원문 제목",
    "sourceName": "연합뉴스",
    "originalUrl": "https://...",
    "publishedAt": "2026-08-03T10:00:00+09:00",
    "analyzedAt": "2026-08-03T23:30:00+09:00",
    "readAt": "2026-08-03T14:20:00+09:00",  // 읽음 기록
    "teacherLabel": "relevant",  // Teacher 분류 (옵션)
    "teacherConfidence": 0.95
  }
]
```

---

## 6. 비용 및 성능

### 6.1 API 호출
```
1단계 (선별): 1번
2단계 (분석): 1번
--------------------
총: 2번
```

### 6.2 토큰 사용량 (100개 기사 → 15개 선별 기준)
```
1단계:
- 입력: 2.5K tokens (제목만)
- 출력: 200 tokens

2단계:
- 입력: 4K tokens (선별된 15개)
- 출력: 6K tokens

총: ~13K tokens (기존 17K 대비 24% 감소)
```

### 6.3 처리 시간 (예상)
```
수집: 10초 (병렬)
필터링: 1초
AI 분석 (2단계): 30초
저장: 2초
--------------------
총: ~43초
```

---

## 7. 실행 트리거

### 7.1 스케줄러
**위치:** `scheduler/`

```java
@Scheduled(cron = "0 30 23 * * *")  // 매일 23:30
public void runDailyBriefing() {
    pipelineRunner.run(PipelineOptions.daily());
}
```

### 7.2 수동 실행
```bash
# API 호출
POST /api/admin/pipeline/run
{
  "targetDate": "2026-08-03",
  "triggerType": "MANUAL"
}
```

---

## 8. 에러 처리

### 8.1 수집 실패
- 개별 소스 실패 시: 로그 기록 후 계속 진행
- 모든 소스 실패 시: 파이프라인 중단

### 8.2 AI 분석 실패
- 재시도 (AppProperties.retry 설정)
- 최종 실패 시: ExecutionLog에 기록, 파이프라인 중단

### 8.3 검증 실패
- 경고: 로그만 기록, 계속 진행
- 치명적 오류: 파이프라인 중단

---

## 9. 주요 설정

### application.yml
```yaml
briefing:
  max-selected-news: 15
  retry:
    max-attempts: 3
    initial-delay-ms: 1000

openai:
  model: gpt-4o
  max-tokens: 16000
```

---

## 10. 디렉토리 구조

```
src/main/java/com/economicbriefing/
├── collector/
│   ├── source/          # 소스 어댑터 (연합뉴스 등)
│   ├── parser/          # RSS 파싱
│   ├── filter/          # 필터링 (날짜, 품질, 중복)
│   └── DefaultNewsCollector.java
├── analyzer/
│   ├── openai/
│   │   ├── dto/         # AiResponse, SelectionResponse
│   │   ├── prompt/      # SelectionPromptBuilder, AnalysisPromptBuilder
│   │   └── OpenAiNewsAnalyzer.java
│   └── NewsAnalyzer.java
├── pipeline/
│   ├── BriefingPipeline.java
│   ├── PipelineDataValidator.java
│   └── ExecutionTracker.java
├── domain/
│   ├── article/         # Article
│   ├── analysis/        # AnalyzedNews
│   └── briefing/        # Briefing
└── api/
    └── BriefingApiController.java
```

---

## 11. 데이터 흐름

```
RSS Feeds (11개 언론사)
    ↓
[수집] DefaultNewsCollector
    - 병렬 수집 (10 threads)
    - 시간 필터
    - 품질 검증
    - 중복 제거
    ↓
List<Article> (100개)
    ↓
[DB 저장] articles 테이블
    ↓
[AI 분석 1단계] 선별
    - GPT-4o
    - 제목만 전달
    - 15개 ID 반환
    ↓
List<String> selectedIds (15개)
    ↓
[AI 분석 2단계] 분석
    - GPT-4o
    - 선별된 15개 상세 분석
    - 순서 기반 매핑
    ↓
Briefing (15개 AnalyzedNews)
    ↓
[필터링] 무관한 뉴스 제거
    ↓
[DB 저장] article_analyses 테이블
    ↓
[API 제공] GET /api/briefing/articles
    ↓
프론트엔드 (React)
```

---

## 12. 개선 이력

### v3-2stage (현재)
- 2단계 파이프라인 도입
- 매핑 오류 원천 차단
- 비용 24% 절감
- 주요 기업 실적 발표 선별 기준 추가

### v2
- 6개 필드 제거 (householdImpact 등)
- 읽음 표시 기능 추가

### v1
- 초기 구현
