# Economic Beginner Briefing

경제를 전혀 모르는 사용자를 위해, 전날의 경제·재테크·부동산 뉴스를 수집하고 중요한 뉴스를 선별한 뒤 쉬운 말로 설명하여 웹으로 제공하는 자동화 프로젝트입니다.

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
- AI: OpenAI API (분석 gpt-4o / 기사 분류 gpt-4o-mini)
- Frontend: React 18 + Vite 6
- RSS: Rome 2.1
- Scheduler: Spring `@Scheduled` (애플리케이션 내장, 매시 정각)
- Test: JUnit 5 + Spring Boot Test + H2

## 전체 구조

```text
src/
├─ main/java/com/economicbriefing/
│  ├─ EconomicBriefingApplication.java   # Spring Boot 진입점
│  ├─ collector/                          # 뉴스 수집 (RSS)
│  ├─ analyzer/                           # AI 분석 (OpenAI)
│  ├─ classifier/                         # Teacher 분류 + 임베딩 + 영속화
│  ├─ api/                                # 공개 브리핑 API
│  ├─ pipeline/                           # 파이프라인 오케스트레이션
│  ├─ domain/                             # 도메인 모델
│  ├─ config/                             # Spring 설정
│  └─ common/                             # 공통 유틸
├─ main/resources/
│  ├─ application.yml                     # 기본 설정
│  ├─ application-test.yml                # 테스트(H2) 설정
│  └─ db/migration/                       # Flyway SQL
└─ test/java/com/economicbriefing/        # 테스트
```

## 실행 흐름

```text
내장 스케줄러(매시 정각) / 관리자 API / CLI
  → Spring Boot 시작
  → BriefingPipeline 실행:
      1. 뉴스 수집 (NewsCollector → RSS → 필터링)
      2. Teacher 분류 + 임베딩 (classifier)
      3. AI 분석 (NewsAnalyzer → OpenAI → Briefing 생성)
      4. article_analyses 저장 → 프론트엔드가 공개 API로 조회
      5. 실행 이력 기록 (pipeline_runs / logs / items)
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
| `SCHEDULER_ENABLED` | 기본값: true | 매시 정각 자동 실행. 끄려면 `false` |
| `SCHEDULER_CRON` | 기본값: `0 0 * * * *` | 6필드 cron, Asia/Seoul 기준 |
| `HEALTH_MAX_SUCCESS_AGE` | 기본값: 3h | 이 시간을 넘기면 `/api/health/briefing`이 DOWN |
| `TZ` | 기본값: Asia/Seoul | 타임존 |
| `DRY_RUN` | 기본값: false | true면 외부 API 호출 없이 Mock 실행 |
| `LOG_LEVEL` | 기본값: info | 로그 레벨 |
| `OPENAI_API_KEY` | 필수 | OpenAI API 키 |
| `ADMIN_TOKEN` | 필수 (DRY_RUN=false) | 관리자 API 인증 토큰. 비어 있으면 기동 실패 |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | 필수 | PostgreSQL 접속 정보 |
| `TEACHER_MODEL` | 기본값: gpt-4o-mini | 기사 분류 모델 |
| `TEACHER_CONCURRENCY` | 기본값: 6 | 분류 동시 실행 수 |
| `EMBEDDING_ENABLED` | 기본값: true | 임베딩 생성 여부 |

`.env.example`을 `.env`로 복사해 사용합니다.

## 수동 실행

```bash
# JAR 빌드 후 실행
./gradlew build
java -jar build/libs/economic-briefing-0.1.0.jar

# 특정 날짜 지정
java -jar build/libs/economic-briefing-0.1.0.jar --target-date=2026-07-16
```

## 자동 실행 (스케줄러)

매시 정각 실행은 **애플리케이션 내부 스케줄러**가 담당합니다. 별도 프로파일 없이 `application.yml`만으로 동작합니다.

- **주기**: `briefing.scheduler.cron` (기본 `0 0 * * * *`, Asia/Seoul) → 직전 1시간 뉴스 수집
- **끄기**: `SCHEDULER_ENABLED=false`
- **중복 실행 방지**: 실행 중이면 skip(`PipelineLock`) + 이미 발행된 시간대면 skip(`pipeline_runs.dedupe_key`)
- **감시**: `GET /api/health/briefing` — 200 UP / 503 DOWN

```
[Scheduler] Pipeline started
[Scheduler] RSS collected : 2
[Scheduler] Teacher completed
[Scheduler] Embedding completed
[Scheduler] Analyze completed
[Scheduler] Pipeline finished (10s) status=SUCCESS
```

## CI

GitHub Actions 워크플로는 없습니다. 자동 실행은 애플리케이션 내장 스케줄러가 담당하고, DB는 운영 서버 로컬에 있어 GitHub 러너에서 접근할 수 없습니다.

빌드·테스트는 로컬에서 실행합니다.

```bash
./gradlew clean build
```

CI를 다시 도입한다면 파이프라인 실행이 아니라 **빌드·테스트 검증용**으로 두는 것이 맞습니다. 러너에서 파이프라인을 돌리려면 `services: postgres`와 외부 접근 가능한 DB가 필요하고, 스케줄 트리거를 걸면 내장 스케줄러와 이중 실행이 됩니다.

## 프론트엔드

**운영**: 빌드하면 백엔드가 `:3000`에서 함께 서빙합니다. 별도 프로세스가 필요 없습니다.

```bash
cd frontend
npm install
npm run build   # frontend/dist 생성 → http://localhost:3000 에서 바로 보임
```

`frontend/dist`는 git에 포함되지 않으므로 **새로 clone하면 반드시 한 번 빌드**해야 합니다.
프론트를 수정한 뒤에도 `npm run build`만 하면 됩니다 (백엔드 재시작 불필요).

**개발**: 핫 리로드가 필요할 때만 개발 서버를 씁니다.

```bash
npm run dev     # http://localhost:5173 (/api 는 :3000 으로 프록시)
```

## 상시 구동 (Windows)

작업 스케줄러에 `EconomicBriefingServer` 작업이 등록되어 있습니다. **로그온 시 자동 시작**하고,
실패하면 1분 간격으로 3회까지 재시도합니다.

```powershell
Get-ScheduledTask  -TaskName EconomicBriefingServer   # 상태 확인
Start-ScheduledTask -TaskName EconomicBriefingServer   # 수동 시작
Stop-ScheduledTask  -TaskName EconomicBriefingServer   # 중지
```

실행 스크립트는 `scripts/run-server.ps1`이며 `.env`를 읽어 JDK 21로 JAR을 띄웁니다.
로그는 `logs/server-YYYY-MM-DD.log`에 하루 단위로 쌓이고 30일이 지나면 자동 삭제됩니다.

> **부팅 시 자동 시작이 필요하면** 관리자 권한으로 트리거를 바꿔야 합니다.
> 현재는 로그온 트리거라 재부팅 후 로그인하지 않으면 서버가 뜨지 않습니다.
>
> ```powershell
> # 관리자 PowerShell에서 실행
> $t = Get-ScheduledTask -TaskName EconomicBriefingServer
> $t.Triggers = (New-ScheduledTaskTrigger -AtStartup)
> $t | Set-ScheduledTask -User "SYSTEM" -RunLevel Highest
> ```

## 관리자 API

`/api/admin/**`는 `ADMIN_TOKEN`이 필요합니다.

```bash
curl -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:3000/api/admin/runs
curl -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
     -d '{"targetDate":"2026-07-25"}' http://localhost:3000/api/admin/runs
```

공개 브리핑 API(`/api/briefing/**`)는 인증이 없습니다.
