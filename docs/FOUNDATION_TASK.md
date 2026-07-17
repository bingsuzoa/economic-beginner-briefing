# Foundation Task for Claude

## 역할

당신은 이 프로젝트의 수석 백엔드 설계자입니다.

`feature/project-foundation` 브랜치에서 후속 기능 브랜치들이 안정적으로 작업할 수 있는 기반을 구현하세요.

작업 전에 다음 문서를 순서대로 읽으세요.

1. `README.md`
2. `docs/PRODUCT_REQUIREMENTS.md`
3. `docs/ARCHITECTURE.md`
4. `docs/DATA_CONTRACTS.md`
5. `docs/AI_EXPLANATION_POLICY.md`
6. `docs/ERROR_HANDLING.md`
7. `docs/BRANCH_TASKS.md`

## 핵심 목표

이 브랜치는 실제 서비스를 완성하는 브랜치가 아닙니다.

뉴스 수집, AI 분석, Notion, 이메일 기능이 서로 다른 브랜치에서 개발되더라도 같은 데이터 규격과 책임 구조를 따르게 만드는 것이 목표입니다.

## 필수 작업

### 1. 프로젝트 초기화

Node.js와 TypeScript 프로젝트를 구성하세요.

필수 조건:

- TypeScript strict mode
- ESM 또는 CommonJS 중 하나를 명확히 선택
- Vitest
- ESLint
- Zod
- 빌드, 테스트, 타입 검사 명령 제공
- Node.js 지원 버전 명시

권장 npm scripts:

```json
{
  "scripts": {
    "dev": "...",
    "build": "...",
    "start": "...",
    "typecheck": "...",
    "test": "...",
    "test:watch": "...",
    "lint": "..."
  }
}
```

### 2. 공통 도메인 타입 구현

`docs/DATA_CONTRACTS.md`를 기준으로 다음 타입을 구현하세요.

- Article
- ArticleSourceType
- NewsCategory
- CollectNewsRequest
- CollectNewsResult
- SourceCollectionReport
- NewsEvidenceStatus
- EconomicTerm
- SourceReference
- AnalyzedNews
- Briefing
- BriefingMetadata
- AudienceProfile
- AnalyzeNewsRequest
- AnalyzeNewsResult
- PublishBriefingRequest
- PublishChannelResult
- PublishBriefingResult
- ExecutionLog
- ExecutionError

필요한 Zod 스키마도 작성하세요.

타입과 스키마가 중복 정의되지 않도록 구조를 신중히 선택하세요.

### 3. 핵심 인터페이스 구현

다음 인터페이스를 정의하세요.

```ts
interface NewsCollector {
  collect(request: CollectNewsRequest): Promise<CollectNewsResult>;
}

interface NewsAnalyzer {
  analyze(request: AnalyzeNewsRequest): Promise<AnalyzeNewsResult>;
}

interface BriefingPublisher {
  publish(request: PublishBriefingRequest): Promise<PublishBriefingResult>;
}
```

### 4. Mock 구현

실제 외부 API를 사용하지 않는 Mock을 작성하세요.

- MockNewsCollector
- MockNewsAnalyzer
- MockBriefingPublisher

Mock 데이터에는 최소 3개 기사를 포함하세요.

권장 주제:

- 기준금리
- 전세 제도
- 예금금리

Mock 분석 결과는 경제 초보자용 설명 형식을 보여줘야 합니다.

### 5. 실행 파이프라인

`runDailyBriefing` 애플리케이션 서비스를 작성하세요.

책임:

- Collector 실행
- 수집 결과 확인
- Analyzer 실행
- 분석 결과 검증
- Publisher 실행
- ExecutionLog 반환

외부 구현체는 생성자 또는 함수 인자로 주입하세요.

애플리케이션 서비스 내부에서 구체 클래스를 직접 생성하지 마세요.

### 6. 날짜 유틸리티

Asia/Seoul 기준으로 다음 기능을 제공하세요.

- 실행일 기준 전날 날짜 계산
- targetDate 시작 시각 계산
- targetDate 종료 시각 계산
- ISO 날짜 유효성 검증

가능하면 시스템 로컬 타임존에 의존하지 않게 구현하세요.

### 7. 환경변수 구조

Zod를 이용해 환경변수 검증 구조를 작성하세요.

Foundation 단계에서는 다음 값만 필수로 해도 됩니다.

- NODE_ENV
- TZ
- DRY_RUN
- LOG_LEVEL

외부 API 키는 선택값으로 정의하세요.

`.env.example`에는 실제 값이 아닌 설명용 빈 값을 작성하세요.

### 8. 에러 모델

다음 항목을 포함하는 공통 오류 구조를 작성하세요.

- error code
- stage
- retryable
- safe message
- cause

로그에 비밀값이 노출되지 않도록 주의하세요.

### 9. 테스트

최소한 다음 테스트를 작성하세요.

- Article 스키마 정상 검증
- 잘못된 URL 거부
- 잘못된 날짜 거부
- 중요도 1~5 범위 검증
- Mock Collector 결과
- Mock Analyzer 결과
- Mock Publisher 결과
- 전체 Mock 파이프라인 성공
- 기사 0개일 때 안전한 실패
- Publisher 일부 실패 결과 표현
- 전날 날짜 계산

### 10. 문서 업데이트

구현 결과에 맞게 다음을 갱신하세요.

- README 실행 방법
- 실제 디렉터리 구조
- 설계 결정
- 후속 브랜치가 구현해야 할 위치

## 금지 사항

이번 브랜치에서는 다음을 구현하지 마세요.

- 실제 RSS 요청
- HTML 크롤링
- OpenAI API 호출
- Notion SDK 호출
- Gmail 또는 이메일 API 호출
- GitHub Actions
- 데이터베이스
- 웹 UI

외부 SDK를 미리 설치할 필요도 없습니다.

## 코드 품질 원칙

- 함수는 한 가지 책임만 갖게 하세요.
- `any` 사용을 피하세요.
- 타입 단언을 최소화하세요.
- 환경변수를 코드 곳곳에서 직접 읽지 마세요.
- 날짜 문자열을 임의로 잘라 계산하지 마세요.
- 테스트가 외부 네트워크에 의존하지 않게 하세요.
- 공통 도메인이 외부 SDK 타입에 의존하지 않게 하세요.

## 작업 완료 보고 형식

작업을 완료한 뒤 다음 형식으로 보고하세요.

```md
## 구현 요약

...

## 생성·수정 파일

- ...

## 주요 설계 결정

- ...

## 테스트 결과

- npm run typecheck:
- npm run test:
- npm run lint:
- npm run build:

## Foundation 완료 조건 확인

- [ ] 공통 타입 구현
- [ ] 인터페이스 구현
- [ ] Mock 구현
- [ ] 전체 Mock 파이프라인
- [ ] 테스트 통과
- [ ] 외부 API 미사용
- [ ] 비밀값 미포함

## 후속 브랜치 주의사항

...

## 남은 문제

...
```

## 최종 지시

문서와 구현 사이에 충돌이 발견되면 임의로 큰 방향을 변경하지 마세요.

다음 우선순위를 따르세요.

1. 사용자에게 쉬운 경제 해설을 제공한다는 제품 목표
2. 데이터 계약의 하위 호환성
3. 모듈 책임 분리
4. 테스트 가능성
5. 구현 편의성

작업이 끝나도 자동으로 다른 브랜치에 병합하지 마세요.
