# Architecture

## 1. 설계 원칙

이 프로젝트는 기능을 다음 세 영역으로 분리한다.

```text
Collect
→ Analyze
→ Publish
```

각 영역은 다른 영역의 구체적인 구현을 몰라야 한다.

예를 들어 뉴스 수집기는 OpenAI API나 Notion API의 존재를 알 필요가 없다.

## 2. 권장 디렉터리 구조

```text
src/
├─ app/
│  ├─ runDailyBriefing.ts
│  └─ createApplication.ts
├─ collectors/
│  ├─ NewsCollector.ts
│  └─ mock/
│     └─ MockNewsCollector.ts
├─ analyzers/
│  ├─ NewsAnalyzer.ts
│  └─ mock/
│     └─ MockNewsAnalyzer.ts
├─ publishers/
│  ├─ BriefingPublisher.ts
│  └─ mock/
│     └─ MockBriefingPublisher.ts
├─ domain/
│  ├─ article.ts
│  ├─ analyzedNews.ts
│  ├─ briefing.ts
│  └─ execution.ts
├─ config/
│  ├─ env.ts
│  └─ constants.ts
├─ errors/
│  ├─ AppError.ts
│  └─ errorCodes.ts
├─ utils/
│  ├─ date.ts
│  └─ result.ts
└─ index.ts

tests/
├─ app/
├─ collectors/
├─ analyzers/
├─ publishers/
└─ fixtures/

docs/
.env.example
package.json
tsconfig.json
vitest.config.ts
eslint.config.js
```

폴더명은 합리적인 이유가 있다면 조정할 수 있으나, Collect·Analyze·Publish의 책임 분리는 유지해야 한다.

## 3. 주요 인터페이스

### NewsCollector

```ts
export interface NewsCollector {
  collect(request: CollectNewsRequest): Promise<CollectNewsResult>;
}
```

책임:

- 지정 기간에 해당하는 기사 수집
- 기사 데이터 정규화
- 수집 단계 오류 보고

책임이 아닌 것:

- 뉴스 중요도 판단
- 초보자용 설명 생성
- 이메일 및 Notion 저장

### NewsAnalyzer

```ts
export interface NewsAnalyzer {
  analyze(request: AnalyzeNewsRequest): Promise<AnalyzeNewsResult>;
}
```

책임:

- 기사 중요도 평가
- 같은 사건 그룹화
- 대표 출처 선정
- 최종 뉴스 선별
- 경제 초보자용 설명 생성
- 사실과 예상 구분

책임이 아닌 것:

- 뉴스 사이트 접근
- Notion 저장
- 이메일 발송

### BriefingPublisher

```ts
export interface BriefingPublisher {
  publish(request: PublishBriefingRequest): Promise<PublishBriefingResult>;
}
```

책임:

- 완성된 브리핑을 외부 채널로 전달
- 전달 성공 및 실패 결과 반환

향후 다음 구현체가 추가될 수 있다.

- `NotionBriefingPublisher`
- `EmailBriefingPublisher`
- `CompositeBriefingPublisher`

## 4. 애플리케이션 서비스

전체 순서를 조율하는 코드는 `runDailyBriefing`에 둔다.

```ts
const collection = await collector.collect(collectRequest);
const analysis = await analyzer.analyze({
  articles: collection.articles,
  targetDate: collectRequest.targetDate,
});
const publication = await publisher.publish({
  briefing: analysis.briefing,
});
```

애플리케이션 서비스는 세부 구현보다 실행 순서, 상태, 오류 정책을 관리한다.

## 5. 의존성 방향

```text
app
↓
interfaces/domain
↑
collectors, analyzers, publishers implementations
```

도메인 타입은 외부 SDK 타입에 의존하면 안 된다.

잘못된 예:

```ts
interface Article {
  notionPage: NotionPageObjectResponse;
}
```

올바른 예:

```ts
interface Article {
  id: string;
  title: string;
  url: string;
}
```

외부 SDK 타입은 각 어댑터 내부에서만 사용한다.

## 6. Foundation의 Mock 파이프라인

Foundation 브랜치에서는 다음 Mock 구현을 제공한다.

- `MockNewsCollector`: 고정된 Article 목록 반환
- `MockNewsAnalyzer`: 고정된 분석 결과 반환
- `MockBriefingPublisher`: 입력값을 메모리에 저장하고 성공 결과 반환

이를 통해 외부 API 없이 다음 전체 흐름을 테스트한다.

```text
Mock 기사 수집
→ Mock 분석
→ Mock 발행
→ 실행 결과 확인
```

## 7. 설정

환경변수는 시작 시 한 번 검증한다.

Foundation 단계에서는 선택값과 기본값만 검증한다.

예상 환경변수:

```env
NODE_ENV=development
TZ=Asia/Seoul
DRY_RUN=true
LOG_LEVEL=info

OPENAI_API_KEY=
NOTION_API_KEY=
NOTION_DATABASE_ID=

EMAIL_PROVIDER=
EMAIL_FROM=
EMAIL_TO=
EMAIL_API_KEY=
```

Foundation의 테스트에서는 외부 API 관련 값이 없어도 실행 가능해야 한다.

실제 구현 브랜치에서는 해당 기능을 사용할 때 필요한 값만 추가 검증한다.

## 8. 확장 방향

향후 다음 기능을 추가할 수 있어야 한다.

- 새로운 RSS 수집처
- 정부기관 보도자료 수집기
- 다른 AI 분석기
- 이메일 제공업체 변경
- 주간 요약
- 월간 경제 흐름 분석
- 사용자별 관심 카테고리
- PostgreSQL 실행 이력 저장
