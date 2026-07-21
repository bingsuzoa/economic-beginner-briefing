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

## 기술 스택

- Language: Java 21
- Framework: Spring Boot 3.4
- Build: Gradle (Kotlin DSL)
- Database: PostgreSQL + Flyway migration
- AI: OpenAI API (gpt-4o)
- Storage: Notion API
- RSS: Rome 2.1
- Scheduler: GitHub Actions
- Test: JUnit 5 + Spring Boot Test + H2

## 전체 구조

```text
src/
├─ main/java/com/economicbriefing/
│  ├─ EconomicBriefingApplication.java   # Spring Boot 진입점
│  ├─ collector/                          # 뉴스 수집 (RSS)
│  ├─ analyzer/                           # AI 분석 (OpenAI)
│  ├─ publisher/                          # 결과 발행 (Notion)
│  ├─ pipeline/                           # 파이프라인 오케스트레이션
│  ├─ domain/                             # 도메인 모델
│  ├─ config/                             # Spring 설정
│  └─ common/                             # 공통 유틸
├─ main/resources/
│  ├─ application.yml                     # 기본 설정
│  ├─ application-prod.yml                # 운영 설정
│  └─ db/migration/                       # Flyway SQL
└─ test/java/com/economicbriefing/        # 테스트
```

## 실행 흐름

```text
CLI / GitHub Actions
  → Spring Boot 시작
  → BriefingPipeline 실행:
      1. 뉴스 수집 (NewsCollector → RSS → 필터링)
      2. AI 분석 (NewsAnalyzer → OpenAI → Briefing 생성)
      3. Notion 저장 (BriefingPublisher)
      4. 실행 결과 반환
  → 종료
```

## 빌드 및 테스트

```bash
./gradlew clean build    # 컴파일 + 테스트 + JAR 패키징
./gradlew test           # 테스트만 실행
```

모든 테스트는 H2 인메모리 DB를 사용하므로 외부 서비스가 필요하지 않습니다.

## 환경변수

| 변수 | 필수 | 설명 |
|------|------|------|
| `SPRING_PROFILES_ACTIVE` | 기본값: default | Spring 프로파일 (prod 등) |
| `TZ` | 기본값: Asia/Seoul | 타임존 |
| `DRY_RUN` | 기본값: true | true면 외부 API 호출 없이 Mock 실행 |
| `LOG_LEVEL` | 기본값: info | 로그 레벨 |
| `OPENAI_API_KEY` | 실제 분석 시 필수 | OpenAI API 키 |
| `NOTION_API_KEY` | Notion 저장 시 필수 | Notion Integration 토큰 |
| `NOTION_DATABASE_ID` | Notion 저장 시 필수 | Notion 데이터베이스 ID |

## 수동 실행

```bash
# JAR 빌드 후 실행
./gradlew build
java -jar build/libs/economic-briefing-0.1.0.jar

# 특정 날짜 지정
java -jar build/libs/economic-briefing-0.1.0.jar --target-date=2026-07-16
```

## GitHub Actions

`.github/workflows/weekly-briefing.yml`로 매시 정각 자동 실행됩니다.

- **주기**: 매시 정각 (UTC) → KST 기준 해당 시간대 뉴스 수집
- **수동 실행**: Actions 탭에서 workflow_dispatch로 실행 가능
- **필요 Secrets**: `OPENAI_API_KEY`, `NOTION_API_KEY`, `NOTION_DATABASE_ID`
- **중복 실행 방지**: concurrency group 설정
- **Watchdog**: `briefing-watchdog.yml`이 스케줄 누락 시 자동 재실행

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
