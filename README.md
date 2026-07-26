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

## CI/CD 운영 방법

`main`에 push되면 GitHub Actions가 **집 데스크탑에 설치된 self-hosted runner**에서 테스트·빌드·배포를
수행합니다. 워크플로는 `.github/workflows/deploy.yml`(이름: `Deploy Economic Briefing`) 하나입니다.

```text
main push
  → GitHub Actions (Deploy Economic Briefing)
  → self-hosted runner (이 PC, LocalSystem 서비스)
  → checkout → JDK 21 → gradlew.bat test
  → scripts\deploy.ps1  (백업 → bootJar + 프론트 빌드 → 서비스 중지 → 산출물 복사 → 재등록·시작)
  → scripts\health-check.ps1
  → 실패하면 이전 JAR로 자동 롤백
```

### 왜 운영 디렉터리를 git pull 하지 않는가

runner는 자기 작업공간(`C:\actions-runner\_work\...`)에 checkout해서 **거기서 빌드**하고,
산출물(JAR, `frontend/dist`)만 운영 디렉터리 `C:\economic-beginner-briefing`으로 복사합니다.
운영 디렉터리의 git 상태(체크아웃된 브랜치, 커밋 안 한 작업)는 배포가 건드리지 않습니다.
운영 디렉터리가 곧 개발 디렉터리이기 때문에 내린 결정입니다.

런타임에 운영 디렉터리에서 실제로 쓰이는 것은 `build\libs\*.jar`, `frontend\dist`, `.env`, `logs` 뿐입니다.

또 하나의 이점은 다운타임입니다. 테스트와 빌드가 작업공간에서 끝난 뒤에야 서비스를 내리므로,
중단 시간은 전체 빌드가 아니라 **재시작 몇 초**입니다.

### GitHub Secrets

**필요한 Secret이 없습니다.** 등록하지 마세요.

| 값 | 어디에 있나 | 이유 |
|---|---|---|
| `OPENAI_API_KEY`, `ADMIN_TOKEN`, DB 접속정보 | 이 PC의 `.env` | 서비스 등록 시 NSSM이 주입. runner가 같은 PC에 있어 GitHub를 거칠 이유가 없음 |
| DB 접속 | `localhost` PostgreSQL | 외부 노출 없음 |
| 배포 자격증명 | 없음 | runner가 배포 대상 PC 자신 |
| runner 등록 토큰 | 설치 때 1회, 1시간 만료 | 저장하지 않음 |

Secret이 필요해지는 경우는 배포 대상이 이 PC가 아니게 되거나(SSH 키 등), 알림을 붙일 때
(`SLACK_WEBHOOK_URL` 등)뿐입니다.

> **보안 주의**: 이 저장소는 public입니다. runner는 SYSTEM 권한으로 동작하므로
> **`pull_request` 트리거를 절대 추가하지 마세요.** fork의 PR이 이 PC에서 임의 코드를 실행하게 됩니다.
> 현재 트리거는 `push: main` 하나뿐이라 협업자만 배포를 유발할 수 있습니다.

### 최초 Runner 설치

1. GitHub → 저장소 → **Settings → Actions → Runners → New self-hosted runner → Windows**
   화면에서 `./config.cmd --token` 뒤의 **등록 토큰**(`AXXXX...`, 1시간 만료)을 복사합니다.
2. **관리자 PowerShell**에서:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install-runner.ps1 -Token AXXXX...
```

최신 runner를 받아 `C:\actions-runner`에 풀고, **LocalSystem 계정 Windows 서비스**로 등록합니다
(로그인 없이 동작, 자동 시작, 크래시 시 재시작). 완료 후 Runners 목록에 `Idle`로 보이면 성공입니다.

LocalSystem을 쓰는 이유: 배포가 `Stop-Service`/`nssm`/`sc.exe`를 호출하므로 관리자 권한이
필요한데, runner 기본 계정(NETWORK SERVICE)에는 그 권한이 없습니다.

```powershell
Get-Service actions.runner.*        # 상태 확인
```

### Runner 업데이트

**보통 아무것도 안 해도 됩니다.** runner는 job 실행 전에 스스로 업데이트합니다.
수동으로 강제하려면 재설치와 같습니다 — 삭제 후 `install-runner.ps1`을 새 토큰으로 다시 실행하세요.
(스크립트는 `C:\actions-runner`에 파일이 이미 있으면 다운로드를 건너뛰므로, 바이너리까지 갈아끼우려면
`C:\actions-runner`를 지우고 실행합니다.)

### Runner 삭제

Settings → Actions → Runners → 해당 runner → **Remove**에서 **제거 토큰**을 받은 뒤,
관리자 PowerShell에서:

```powershell
cd C:\actions-runner
.\config.cmd remove --token AXXXX...
```

서비스 등록까지 함께 해제됩니다. 남은 파일은 `Remove-Item C:\actions-runner -Recurse -Force`.

토큰을 받을 수 없는 상태(예: 저장소가 이미 삭제됨)라면 서비스만 강제로 제거합니다.

```powershell
$n = (Get-Service actions.runner.*).Name
Stop-Service $n; sc.exe delete $n
```

### 자동 배포

`main`에 push하거나 PR을 merge하면 자동 실행됩니다. Actions 탭에서 진행 상황을 봅니다.
동시 실행은 `concurrency: deploy-production`으로 직렬화되므로 두 배포가 겹치지 않습니다.

배포 로그는 GitHub Actions 외에 이 PC에도 남습니다: `logs\deploy-<타임스탬프>.log`.

### 수동 배포

runner나 GitHub가 안 될 때, 또는 `.env`만 바꿨을 때 씁니다. **관리자 PowerShell에서**:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\deploy.ps1
```

이 경우 운영 디렉터리에서 그대로 빌드합니다(in-place). 서비스가 JAR을 잠그므로 빌드 전에
서비스를 먼저 내립니다 — CI 경로보다 중단 시간이 깁니다.

| 옵션 | 용도 |
|---|---|
| `-Test` | 빌드 전에 `gradlew test` 실행 (CI는 별도 스텝이라 안 씀) |
| `-SkipFrontend` | 프론트 재빌드 생략 |
| `-HealthTimeoutSec 300` | 헬스 체크 대기 시간 (기본 180초) |
| `-ProdRoot <경로>` | 다른 디렉터리에서 빌드해 운영 디렉터리로 배포 (CI가 쓰는 방식) |

`.env`만 수정한 경우는 `scripts\install-service.ps1`만 다시 실행해도 됩니다.
환경변수는 서비스 등록 시점에 주입되므로 재시작만으로는 반영되지 않습니다.

### 백업과 롤백

`deploy.ps1`은 배포 전에 현재 JAR과 `frontend/dist`를 `backup\<타임스탬프>\`에 복사하고
**최근 3개만** 남깁니다(JAR 하나가 64MB). `backup/`은 git에 포함되지 않습니다.

새 릴리스가 헬스 체크를 통과하지 못하면 **자동으로 이전 JAR을 되돌리고 다시 기동**한 뒤
헬스를 재확인합니다. 종료 코드로 결과를 구분합니다.

| 코드 | 의미 |
|---|---|
| `0` | 배포 성공 |
| `1` | 배포 실패, **롤백 성공** (운영은 이전 버전으로 정상) |
| `2` | 배포 실패, **롤백도 실패** — 즉시 수동 조치 필요 |

수동 롤백:

```powershell
Get-ChildItem backup                                    # 시점 확인
Copy-Item backup\<타임스탬프>\*.jar build\libs\ -Force
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install-service.ps1
```

### 헬스 체크가 503인데 실패가 아닌 경우

`/api/health/briefing`은 "브리핑이 계속 생산되고 있는가"에 답하므로, 마지막 성공 실행이
3시간(`HEALTH_MAX_SUCCESS_AGE`)을 넘으면 DOWN을 반환합니다. 재시작은 파이프라인을 돌리지 않고
다음 정각을 기다리므로, **마지막 실행 후 3시간이 지난 시점의 배포는 정상인데도 503**이 됩니다.

`scripts\health-check.ps1`은 이 경우만 예외로 통과시킵니다 — `dbConnected: true`이고
사유가 `no successful run ...` 하나뿐일 때. DB 장애나 cron 오설정은 그대로 실패합니다.
literal 200을 요구하려면 `-Strict`를 붙입니다.

### 장애 시 대응

| 증상 | 원인 후보 | 조치 |
|---|---|---|
| Actions가 `Waiting for a runner`에서 멈춤 | runner 서비스 중지 / PC 꺼짐 | `Get-Service actions.runner.*` → `Start-Service`. Runners 목록에서 `Offline` 확인 |
| `Test` 스텝 실패 | 실제 테스트 실패 | 운영은 **무손상**(서비스를 내리기 전에 중단됨). 코드 수정 후 재push |
| `Deploy` 스텝 exit 1 | 새 릴리스가 헬스 실패 | **롤백 완료 상태**. Actions 로그와 `logs\deploy-*.log`에서 원인 확인 |
| `Deploy` 스텝 exit 2 | 롤백까지 실패 | 서비스가 내려가 있을 수 있음. 위 "수동 롤백" 절차 실행 |
| 배포는 성공인데 도메인 502 | Cloudflare Tunnel | `Restart-Service cloudflared` (관리자) |
| `Access is denied` / nssm 실패 | runner가 관리자 권한이 아님 | runner 서비스 계정이 `LocalSystem`인지 확인, 아니면 `install-runner.ps1` 재실행 |
| 모든 스텝이 1초 안에 exit 1 | `running scripts is disabled on this system` — SYSTEM의 실효 실행 정책이 Restricted | 워크플로의 `defaults.run.shell`이 `-ExecutionPolicy Bypass`를 포함하는지 확인. 지우면 재발합니다 |
| DB 연결 실패 | postgres 중지 | `Get-Service postgresql-x64-17` → `Start-Service` |
| 배포가 서로 겹침 | — | `concurrency`가 직렬화하므로 발생하지 않음. 겹쳤다면 워크플로에서 `concurrency` 확인 |

runner를 못 쓰는 상황에서는 항상 **수동 배포**로 대체할 수 있습니다. CI/CD는 수동 절차를
대체한 게 아니라 감싼 것이라, 같은 `deploy.ps1`을 호출합니다.

### 파이프라인은 CI에서 돌리지 않습니다

브리핑 파이프라인 실행은 여전히 **애플리케이션 내장 스케줄러**가 담당합니다. Actions에
`schedule` 트리거를 걸면 내장 스케줄러와 이중 실행이 되고, GitHub 호스팅 러너는 로컬 DB에
접근할 수도 없습니다. CI는 **배포만** 합니다.

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

## 상시 구동 (Windows 서비스)

운영은 **NSSM으로 감싼 Windows 서비스 `EconomicBriefing`** 입니다. `java -jar`로 bootJar
산출물을 직접 띄우며, `gradlew bootRun`은 운영에 쓰지 않습니다(호출하면 실패하도록 막아둠).

| 항목 | 설정 |
|---|---|
| 시작 유형 | 자동(지연 시작) — 로그인 여부와 무관 |
| 실행 계정 | `LocalSystem` |
| 의존 서비스 | `postgresql-x64-17` |
| 프로세스 종료 시 | NSSM이 5초 후 재시작 (+ SCM 복구 동작 3단계) |
| 중복 실행 | SCM이 단일 인스턴스 보장, 설치 시 3000 포트 점유 프로세스 정리 |
| 로그 | `logs/service-stdout.log`, `logs/service-stderr.log` (일 단위 / 10MB 로테이션) |

```powershell
Get-Service EconomicBriefing        # 상태 확인
Restart-Service EconomicBriefing    # 재시작 (관리자)
Stop-Service EconomicBriefing       # 중지 (관리자)
```

### 배포 (코드 수정 후)

평소에는 **`main`에 merge하면 자동 배포**됩니다 — "CI/CD 운영 방법" 참고.
아래는 runner나 GitHub를 못 쓸 때의 수동 경로입니다. **관리자 PowerShell에서** 실행합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\deploy.ps1
```

서비스 중지 → `gradlew clean bootJar` → 서비스 재등록·시작을 순서대로 수행합니다.
서비스가 떠 있는 동안에는 JVM이 JAR 파일을 잠그기 때문에 `gradlew clean`이 실패합니다.
**`gradlew`를 직접 호출하지 말고 이 스크립트를 쓰세요.**

`.env`를 수정한 경우에도 이 스크립트(또는 `scripts\install-service.ps1`)를 다시 실행해야 합니다.
환경변수는 서비스 등록 시점에 주입되므로 재시작만으로는 반영되지 않습니다.

재부팅 후 자동 시작이 정상인지 확인하려면 `docs/REBOOT_TEST.md`의 절차를 실행합니다.

### 모니터링

`GET /api/health/briefing`은 인증 없이 `200 UP` / `503 DOWN`을 반환합니다.
개인 PC 구성이라 감시는 외부에 두어야 합니다 — UptimeRobot 설정은 `docs/MONITORING.md` 참고.

### 최초 설치

`tools/nssm.exe`가 없으면 먼저 받습니다(git에 포함되지 않음).

```powershell
Invoke-WebRequest https://nssm.cc/release/nssm-2.24.zip -OutFile $env:TEMP\nssm.zip
Expand-Archive $env:TEMP\nssm.zip $env:TEMP -Force
New-Item -ItemType Directory tools -Force | Out-Null
Copy-Item $env:TEMP\nssm-2.24\win64\nssm.exe tools\nssm.exe
```

그 다음 관리자 PowerShell에서 `scripts\install-service.ps1`을 실행합니다.

## 관리자 API

`/api/admin/**`는 `ADMIN_TOKEN`이 필요합니다.

```bash
curl -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:3000/api/admin/runs
curl -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
     -d '{"targetDate":"2026-07-25"}' http://localhost:3000/api/admin/runs
```

공개 브리핑 API(`/api/briefing/**`)는 인증이 없습니다.
