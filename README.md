# Economic Beginner Briefing

경제를 전혀 모르는 사용자를 위해, 전날의 경제·재테크·부동산 뉴스를 수집하고 중요한 뉴스를 선별한 뒤 쉬운 말로 설명하여 Notion에 저장하는 자동화 프로젝트입니다.

## 프로젝트 목표

단순한 뉴스 요약이 아니라, 사용자가 다음 질문에 답을 얻도록 합니다.

- 무슨 일이 발생했는가?
- 기존에는 어떤 상황이었는가?
- 무엇이 달라졌는가?
- 왜 이런 변화가 생겼는가?
- 일반 가정과 신혼부부에게 어떤 영향이 있는가?
- 앞으로 어떤 일이 발생할 가능성이 있는가?
- 지금 확인하거나 행동할 것이 있는가?
- 기사에 나온 경제용어는 무슨 뜻인가?

## 전체 구조

```text
src/
├─ app/                     # 파이프라인 오케스트레이션
│  ├─ createApplication.ts  # DI 컨테이너
│  ├─ createDefaultApplication.ts  # 실제/Mock 자동 선택
│  ├─ runDailyBriefing.ts   # Collect → Analyze → Publish 순차 실행
│  ├─ ExecutionTracker.ts   # 중복 실행 방지
│  └─ validatePipelineData.ts  # 단계 간 데이터 검증
├─ cli/                     # CLI 진입점
│  └─ runDailyBriefingCli.ts
├─ scheduler/               # 스케줄 옵션 파싱
│  └─ schedulerOptions.ts
├─ collectors/              # 뉴스 수집
│  ├─ RealNewsCollector.ts  # RSS 기반 수집
│  ├─ sources/              # 언론사별 RSS 어댑터
│  ├─ filters/              # 날짜/품질/중복/카테고리 필터
│  ├─ parsers/              # RSS 파싱, 기사 정규화
│  └─ mock/                 # Mock 수집기
├─ analyzers/               # AI 분석
│  ├─ openai/               # OpenAI 기반 분석기
│  │  ├─ OpenAIClient.ts
│  │  ├─ OpenAINewsAnalyzer.ts
│  │  ├─ prompts/           # 시스템 프롬프트, 응답 스키마
│  │  └─ utils/             # 브리핑 변환, 재시도
│  └─ mock/                 # Mock 분석기
├─ publishers/              # 결과 발행
│  ├─ notion/               # Notion 저장
│  │  ├─ NotionBriefingPublisher.ts
│  │  ├─ NotionClientAdapter.ts
│  │  └─ buildNotionPage.ts
│  └─ mock/                 # Mock 발행기
├─ domain/                  # 공통 타입
├─ config/                  # 환경변수, 상수
├─ errors/                  # 에러 타입
└─ utils/                   # 날짜, Result 유틸
```

## 실행 흐름

```text
CLI / GitHub Actions
  → schedulerOptions 파싱 (날짜, 모드)
  → createDefaultApplication (환경 기반 구현체 선택)
  → runDailyBriefing:
      1. 중복 실행 확인 (ExecutionTracker)
      2. 뉴스 수집 (RealNewsCollector → RSS → 필터링)
      3. 수집 결과 검증 (validateCollectResult)
      4. AI 분석 (OpenAINewsAnalyzer → Briefing 생성)
      5. 분석 결과 검증 (validateAnalyzeResult)
      6. Notion 저장 (NotionBriefingPublisher)
      7. ExecutionLog 반환
  → JSON 결과 출력 + exit code
```

## 설치

```bash
git clone <repository-url>
cd economic-beginner-briefing
npm install
```

## 환경변수

`.env.example`을 복사하여 `.env`를 만드세요.

```bash
cp .env.example .env
```

| 변수 | 필수 | 설명 |
|------|------|------|
| `NODE_ENV` | 기본값: development | 실행 환경 |
| `TZ` | 기본값: Asia/Seoul | 타임존 |
| `DRY_RUN` | 기본값: true | true면 외부 API 호출 없이 Mock 실행 |
| `LOG_LEVEL` | 기본값: info | 로그 레벨 (debug, info, warn, error) |
| `OPENAI_API_KEY` | 실제 분석 시 필수 | OpenAI API 키 |
| `NOTION_API_KEY` | Notion 저장 시 필수 | Notion Integration 토큰 |
| `NOTION_DATABASE_ID` | Notion 저장 시 필수 | Notion 데이터베이스 ID |

API 키 없이도 `DRY_RUN=true`로 Mock 파이프라인을 실행할 수 있습니다.

## 로컬 테스트

```bash
npm run typecheck    # TypeScript 타입 검사
npm run lint         # ESLint 검사
npm test             # Vitest 단위/통합 테스트
npm run build        # TypeScript 빌드
```

모든 테스트는 Mock을 사용하므로 외부 API 키가 필요하지 않습니다.

## 수동 실행

### Mock (DRY_RUN) 실행

```bash
DRY_RUN=true npm run briefing:run
```

### 특정 날짜 지정 실행

```bash
npm run briefing:run -- --target-date 2026-07-16
```

### 실제 API 연동 실행

```bash
DRY_RUN=false \
OPENAI_API_KEY=your-key \
NOTION_API_KEY=your-key \
NOTION_DATABASE_ID=your-db-id \
npm run briefing:run
```

실행 결과는 JSON으로 stdout에 출력되며, exit code 0이면 성공, 1이면 실패입니다.

## GitHub Actions

`.github/workflows/weekly-briefing.yml`로 자동 실행됩니다.

- **주기**: 매주 월요일 04:30 KST (일요일 19:30 UTC)
- **수동 실행**: Actions 탭에서 workflow_dispatch로 실행 가능
- **필요 Secrets**: `OPENAI_API_KEY`, `NOTION_API_KEY`, `NOTION_DATABASE_ID`
- **중복 실행 방지**: concurrency group 설정

매일 실행이 필요하면 cron을 `30 19 * * *`로 변경하세요.

## Notion 연동

1. [Notion Integrations](https://www.notion.so/my-integrations)에서 Integration을 생성합니다.
2. 브리핑을 저장할 데이터베이스를 만들고, 다음 속성을 추가합니다:
   - `Name` (title)
   - `Briefing ID` (rich_text)
   - `Target Date` (date)
   - `Generated At` (date)
   - `News Count` (number)
3. 데이터베이스에 Integration을 연결합니다.
4. `NOTION_API_KEY`와 `NOTION_DATABASE_ID`를 환경변수에 설정합니다.

## 장애 확인

파이프라인 실행 결과 JSON의 `execution` 필드를 확인합니다.

```json
{
  "execution": {
    "status": "failed",
    "errors": [
      {
        "stage": "collect",
        "code": "COLLECT_SOURCE_TIMEOUT",
        "message": "RSS feed timeout",
        "retryable": true
      }
    ]
  }
}
```

- `stage`: 실패 단계 (collect, analyze, publish, system)
- `code`: 에러 코드 (errorCodes.ts 참조)
- `retryable`: 재시도 가능 여부

GitHub Actions에서는 Actions 탭의 workflow run 로그에서 확인합니다.

## 미구현 기능

- **Email Publisher**: `feature/email-publisher` 브랜치에서 구현 예정
- **영구 실행 이력 저장**: 현재 MockExecutionTracker (인메모리)만 사용. 프로세스 재시작 시 초기화됨.

## 기술 스택

- Runtime: Node.js 20+
- Language: TypeScript (strict mode)
- Test: Vitest
- Validation: Zod
- AI: OpenAI API (gpt-4o)
- Storage: Notion API
- Scheduler: GitHub Actions
- Package manager: npm
