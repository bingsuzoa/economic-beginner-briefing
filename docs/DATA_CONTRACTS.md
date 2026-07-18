# Data Contracts

이 문서는 브랜치 간 데이터 규격을 정의한다.

다른 브랜치에서 공통 타입을 임의로 수정하지 않는다. 변경이 필요하면 `CHANGE_REQUEST.md`에 이유와 호환성 영향을 작성한다.

## 1. 공통 기본 타입

```ts
export type ISODate = string;
export type ISODateTime = string;
export type UrlString = string;
```

Foundation 구현에서는 문자열 별칭으로 시작할 수 있다. 필요하면 Zod 검증 스키마를 함께 제공한다.

## 2. Article

수집기가 반환하는 정규화된 기사다.

```ts
export interface Article {
  id: string;
  title: string;
  summary: string;
  sourceName: string;
  sourceType: ArticleSourceType;
  publishedAt: ISODateTime;
  collectedAt: ISODateTime;
  url: UrlString;
  categories: NewsCategory[];
  language: "ko";
  content?: string;
}
```

```ts
export type ArticleSourceType =
  | "news_media"
  | "government"
  | "public_institution"
  | "financial_institution"
  | "other";
```

### 필드 규칙

- `id`: 동일 기사를 식별하는 안정적인 값
- `title`: 빈 문자열 금지
- `summary`: RSS 설명 또는 본문 요약. 없으면 빈 문자열 허용
- `sourceName`: 언론사 또는 기관명
- `publishedAt`: 원문 게시 시각
- `collectedAt`: 프로그램이 수집한 시각
- `url`: 원문 URL
- `categories`: 하나 이상 권장
- `content`: 원문 전체 또는 정제 본문. 저작권과 토큰 비용을 고려하여 선택적으로 사용

## 3. NewsCategory

```ts
export type NewsCategory =
  | "interest_rate"
  | "deposit_saving"
  | "loan"
  | "housing"
  | "jeonse_monthly_rent"
  | "subscription"
  | "tax"
  | "pension"
  | "insurance"
  | "cost_of_living"
  | "exchange_rate"
  | "investment"
  | "government_support"
  | "employment_income"
  | "household_debt"
  | "other";
```

## 4. CollectNewsRequest

```ts
export interface CollectNewsRequest {
  targetDate: ISODate;
  timezone: "Asia/Seoul";
  maxArticles?: number;
}
```

`targetDate`는 수집할 뉴스의 날짜이며 실행일과 다를 수 있다.

## 5. CollectNewsResult

```ts
export interface CollectNewsResult {
  targetDate: ISODate;
  articles: Article[];
  sourceReports: SourceCollectionReport[];
  totalCollected: number;
  totalAccepted: number;
  totalRejected: number;
}
```

```ts
export interface SourceCollectionReport {
  sourceName: string;
  status: "success" | "partial" | "failed";
  collectedCount: number;
  acceptedCount: number;
  errorCode?: string;
  errorMessage?: string;
}
```

한 수집처가 실패해도 다른 수집처 결과는 반환할 수 있다.

## 6. NewsEvidenceStatus

```ts
export type NewsEvidenceStatus =
  | "confirmed"
  | "proposed"
  | "expected";
```

- `confirmed`: 공식적으로 확인된 사실
- `proposed`: 계획, 검토, 입법예고, 추진안
- `expected`: 논리적 예상 또는 파급 효과

## 7. EconomicTerm

```ts
export interface EconomicTerm {
  term: string;
  explanation: string;
  example?: string;
}
```

설명은 경제 초보자가 이해할 수 있어야 한다.

## 8. SourceReference

```ts
export interface SourceReference {
  articleId: string;
  sourceName: string;
  title: string;
  url: UrlString;
  publishedAt: ISODateTime;
  isPrimary: boolean;
}
```

가능하면 정부기관 또는 공식기관 자료를 대표 출처로 우선 지정한다.

## 9. AnalyzedNews

```ts
export interface AnalyzedNews {
  id: string;
  representativeTitle: string;
  category: NewsCategory;
  importance: 1 | 2 | 3 | 4 | 5;
  relevanceReason: string;

  oneLineSummary: string;
  explanation: string;
  expectedNextEffects: string[];
  recommendedChecks: string[];

  evidenceStatus: NewsEvidenceStatus;
  uncertaintyNote?: string;

  economicTerms: EconomicTerm[];
  sources: SourceReference[];
}
```

### 설명 필드 원칙

- 같은 내용을 반복하지 않는다.
- 비어 있는 내용을 억지로 만들어내지 않는다.
- 해당 항목이 기사에서 확인되지 않으면 `확인된 내용이 부족합니다`와 같은 명시적 표현을 사용한다.
- `expectedNextEffects`는 예측이며 확정 사실처럼 작성하면 안 된다.
- `recommendedChecks`는 투자 매수·매도 지시가 아니라 확인할 항목이어야 한다.

## 10. Briefing

```ts
export interface Briefing {
  id: string;
  targetDate: ISODate;
  generatedAt: ISODateTime;
  title: string;
  overallSummary: string[];
  news: AnalyzedNews[];
  glossary: EconomicTerm[];
  metadata: BriefingMetadata;
}
```

```ts
export interface BriefingMetadata {
  collectedArticleCount: number;
  analyzedArticleCount: number;
  selectedNewsCount: number;
  modelName?: string;
  promptVersion?: string;
}
```

## 11. AnalyzeNewsRequest

```ts
export interface AnalyzeNewsRequest {
  targetDate: ISODate;
  articles: Article[];
  maxSelectedNews: number;
  audience: AudienceProfile;
}
```

```ts
export interface AudienceProfile {
  economicKnowledgeLevel: "beginner";
  interests: NewsCategory[];
  contextNotes: string[];
}
```

기본 audience 예시:

```ts
{
  economicKnowledgeLevel: "beginner",
  interests: [
    "interest_rate",
    "loan",
    "housing",
    "jeonse_monthly_rent",
    "deposit_saving",
    "government_support"
  ],
  contextNotes: [
    "신혼부부",
    "주택 구입과 출산 준비에 관심이 있음",
    "경제용어 설명이 필요함"
  ]
}
```

## 12. AnalyzeNewsResult

```ts
export interface AnalyzeNewsResult {
  briefing: Briefing;
  rejectedArticleIds: string[];
  warnings: string[];
}
```

## 13. PublishBriefingRequest

```ts
export interface PublishBriefingRequest {
  briefing: Briefing;
  dryRun: boolean;
}
```

## 14. PublishChannelResult

```ts
export interface PublishChannelResult {
  channel: "email" | "notion" | "mock";
  status: "success" | "skipped" | "failed";
  externalId?: string;
  errorCode?: string;
  errorMessage?: string;
}
```

## 15. PublishBriefingResult

```ts
export interface PublishBriefingResult {
  briefingId: string;
  results: PublishChannelResult[];
  completedAt: ISODateTime;
}
```

## 16. ExecutionLog

```ts
export interface ExecutionLog {
  executionId: string;
  targetDate: ISODate;
  startedAt: ISODateTime;
  completedAt?: ISODateTime;
  status: "running" | "success" | "partial_success" | "failed";
  collectedArticleCount: number;
  selectedNewsCount: number;
  errors: ExecutionError[];
}
```

```ts
export interface ExecutionError {
  stage: "collect" | "analyze" | "publish" | "system";
  code: string;
  message: string;
  retryable: boolean;
  sourceName?: string;
}
```

## 17. 호환성 규칙

공통 타입 변경 시 다음 원칙을 따른다.

- 기존 필드 삭제 금지
- 필드 이름 변경 금지
- 필수 필드 추가는 다른 브랜치와 합의 후 진행
- 가능한 경우 선택 필드로 먼저 추가
- Enum 값 삭제 금지
- 날짜는 ISO 형식을 사용
- 금액이 추가될 경우 부동소수점 대신 정수 원 단위를 우선 사용
