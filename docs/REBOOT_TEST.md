# 재부팅 테스트 런북

> 이 문서는 **재부팅 후 자동 시작 검증**을 위한 자기완결 런북입니다.
> 맥락을 모르는 새 세션이 이 파일만 읽고도 합격/불합격을 판정할 수 있게 작성되었습니다.
> 작성 시각: 2026-07-26 15:40 KST / 커밋 `4fb2af1`
> **검증 완료: 2026-07-26 15:46 KST — 합격 (10/10)**. 결과는 §7 참고. 이후 재검증에도 그대로 사용합니다.

## 1. 무엇을 검증하는가

2026-07-26에 이 프로젝트를 개발 실행(`gradlew bootRun`)에서 **Windows 서비스 운영**으로 전환했습니다.
전환 후 ①~⑩ 항목에 이어 **⑪ 재부팅 후 자동 실행도 2026-07-26 15:46 검증 완료**입니다(§7).
아래 절차는 서비스 구성이나 JDK/DB를 손댄 뒤 재검증할 때 다시 씁니다.

검증해야 할 것은 세 가지입니다.

1. 재부팅 후 **아무도 시작시키지 않았는데** 서비스가 떠 있는가
2. **로그인하지 않아도** 떠 있는가 (= 로그온 트리거가 아니라 부팅 트리거인가)
3. PostgreSQL보다 **늦게** 떠서 DB 연결에 성공했는가

## 2. 현재 구성 (기대값)

| 항목 | 값 |
|---|---|
| 서비스 이름 | `EconomicBriefing` |
| 서비스 래퍼 | NSSM 2.24 (`tools\nssm.exe`) |
| 시작 유형 | `AUTO_START (DELAYED)` |
| 실행 계정 | `LocalSystem` |
| 의존 서비스 | `postgresql-x64-17` |
| 실행 파일 | `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe` |
| JAR | `C:\economic-beginner-briefing\build\libs\economic-briefing-0.1.0.jar` |
| 포트 | 3000 |
| 스케줄러 | 매시 정각 (`0 0 * * * *`, Asia/Seoul) |
| 로그 | `logs\service-stdout.log`, `logs\service-stderr.log` |
| 공개 도메인 | <https://economic-beginner.com> (Cloudflare Tunnel, 별도 서비스 `cloudflared`) |

관련 문서: `README.md`(상시 구동 섹션), `docs/MONITORING.md`, `docs/SCHEDULER_OPERATION.md`

## 3. 검증 절차

재부팅 후 **3분 이상 기다린 뒤** 실행합니다. 지연 시작이라 즉시 뜨지 않는 것이 정상입니다.
이 PC의 실측 기동 시점은 **부팅 후 약 130초**이며, 그 전에는 서비스가 `Stopped`이고
`Service Control Manager` 이벤트조차 없습니다. **1~2분 시점의 `Stopped`는 실패가 아닙니다.**
관리자 권한은 필요 없습니다.

```powershell
$OutputEncoding = [Console]::OutputEncoding = [Text.Encoding]::UTF8
Set-Location C:\economic-beginner-briefing

$boot    = (Get-CimInstance Win32_OperatingSystem).LastBootUpTime
$svc     = Get-CimInstance Win32_Service -Filter "Name='EconomicBriefing'"
$delayed = (Get-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Services\EconomicBriefing' -Name DelayedAutoStart -ErrorAction SilentlyContinue).DelayedAutoStart
$pid3000 = (Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue).OwningProcess
# 주의: 서비스가 LocalSystem이라 비승격 셸에서는 Get-Process의 StartTime/Owner를 읽지 못합니다.
# CIM(Win32_Process)은 읽히므로 시간 계산은 반드시 CreationDate를 씁니다.
$jproc   = if ($pid3000) { Get-CimInstance Win32_Process -Filter "ProcessId=$pid3000" } else { $null }
$parent  = if ($jproc) { (Get-CimInstance Win32_Process -Filter "ProcessId=$($jproc.ParentProcessId)").Name } else { 'N/A' }

"[부팅 시각]      $boot"
"[서비스 상태]    $($svc.State)"
"[시작 유형]      $($svc.StartMode)  DelayedAutoStart=$delayed"
"[서비스 계정]    $($svc.StartName)"
"[JVM 시작]       $(if ($jproc) { $jproc.CreationDate } else { 'N/A - 프로세스 없음' })"
"[부팅->기동]     $(if ($jproc) { '{0:N0}초' -f ($jproc.CreationDate - $boot).TotalSeconds } else { 'N/A' })"
"[JVM 세션 ID]    $(if ($jproc) { $jproc.SessionId } else { 'N/A' })   (내 셸 세션=$((Get-Process -Id $PID).SessionId))"
"[부모 프로세스]  $parent"
"[postgres]       $((Get-Service postgresql-x64-17).Status)"
"[cloudflared]    $((Get-Service cloudflared).Status)"
"[헬스]           $(try { (Invoke-WebRequest 'http://localhost:3000/api/health/briefing' -UseBasicParsing -TimeoutSec 20).Content } catch { "HTTP $($_.Exception.Response.StatusCode.value__)" })"
"[도메인]         $(try { (Invoke-WebRequest 'https://economic-beginner.com' -UseBasicParsing -TimeoutSec 30).StatusCode } catch { 'FAIL' })"
"--- 부팅 이후 로그 ---"
Get-Content logs\service-stdout.log -Tail 200 |
    Select-String 'Started EconomicBriefing|\[Scheduler\]|Configuration validated|ERROR|Exception' |
    Select-Object -Last 10
```

## 4. 합격 기준

전부 만족해야 합격입니다.

| # | 확인 항목 | 합격 조건 | 무엇을 증명하는가 |
|---|---|---|---|
| 1 | 서비스 상태 | `Running` | 살아 있음 |
| 2 | 시작 유형 | `Auto` + `DelayedAutoStart=1` | 부팅 트리거 (로그온 트리거 아님) |
| 3 | 서비스 계정 | `LocalSystem` | 사용자 계정에 매이지 않음 |
| 4 | JVM 세션 ID | `0` (내 셸은 보통 `1`) | 사용자 세션이 아닌 **서비스 세션**에서 실행 중 |
| 5 | 부모 프로세스 | `nssm.exe` | 수동 실행이 아니라 서비스로 기동됨 |
| 6 | 부팅→기동 시간 | 대략 30~300초 | 지연 시작이 실제로 동작 (참고용, 엄격한 기준 아님) |
| 7 | postgres / cloudflared | 둘 다 `Running` | 의존 서비스 정상 |
| 8 | 헬스 | `{"status":"UP", ... "dbConnected":true}` | 앱+DB 정상 (아래 예외 참고) |
| 9 | 도메인 | `200` | 터널 정상 |
| 10 | 로그 | `Started EconomicBriefingApplication` + `[Scheduler] ENABLED` 있고, 부팅 이후 `ERROR`/`Exception` 없음 | 정상 기동 |

**2·3·4번이 로그인 무관 실행의 핵심 증거입니다.**
"로그온 시각보다 서비스가 먼저 떴는가"로 판정하면 **안 됩니다**. 이 PC는 부팅 약 10초 만에
`explorer.exe`가 뜨는 반면 서비스는 지연 시작이라 항상 나중에 뜹니다. 정상인데도 실패로 보입니다.
세션 ID가 `0`이라는 사실이 사용자 세션과 무관하게 실행 중임을 직접 증명합니다.

### 주의: 헬스가 `503 DOWN`인데 실패가 아닌 경우

```json
{"status":"DOWN", "dbConnected":true, "reasons":["no successful run in NNNm (limit 180m)"]}
```

`dbConnected: true`이고 `reasons`가 **`no successful run`만 있다면 자동 시작은 성공한 것**입니다.
재부팅으로 인해 3시간 넘게 파이프라인이 안 돌았을 뿐이며, 다음 정각에 자동 복구됩니다.
→ 이 경우 **다음 정각까지 기다렸다가** `logs\service-stdout.log`에 해당 시각
`[Scheduler] Pipeline finished ... status=SUCCESS`가 찍히는지 확인하면 완전 합격입니다.

## 5. 불합격 시 진단

| 증상 | 원인 후보 | 조치 |
|---|---|---|
| 서비스 `Stopped` | **부팅 3분 이내면 지연 시작 대기 중(정상)**. 그 이후면 시작 실패 | `logs\service-stderr.log`, `Get-EventLog System -Source 'Service Control Manager' -Newest 30` |
| 서비스 없음 | 등록이 풀림 | 관리자 PS에서 `scripts\install-service.ps1` |
| `Running`인데 포트 없음 | JVM 크래시 후 재시작 루프 | `logs\service-stdout.log` 마지막 예외 확인 |
| DB 연결 실패 | postgres보다 먼저 뜸 | 의존성 확인: `sc qc EconomicBriefing`에 `postgresql-x64-17` |
| `java.exe` 못 찾음 | JDK 경로 변경 | Temurin 21 재설치 후 `scripts\install-service.ps1` 재실행 |
| 로컬 200 / 도메인 실패 | 터널 문제 | `Restart-Service cloudflared` (관리자) |

**중요**: 코드나 `.env`를 고친 뒤에는 반드시 관리자 PowerShell에서
`scripts\deploy.ps1`을 실행해야 합니다. 서비스가 떠 있으면 JVM이 JAR을 잠가
`gradlew clean`이 실패하고, `.env` 값은 서비스 등록 시점에 주입되므로 재시작만으로는 반영되지 않습니다.

## 6. Claude에게 검증을 시키는 방법

재부팅 후 Claude Code를 `C:\economic-beginner-briefing`에서 실행하고 아래처럼 지시하면 됩니다.

```
docs/REBOOT_TEST.md 읽고 재부팅 테스트 검증해줘
```

이 한 줄이면 충분합니다. 이 문서에 구성·절차·합격 기준·진단이 모두 들어 있어
이전 대화 맥락 없이도 판정할 수 있습니다.

문제가 있으면 이어서:

```
실패한 항목 원인 분석하고 고쳐줘
```

다음 정각 스케줄러 실행까지 확인하고 싶다면:

```
docs/REBOOT_TEST.md 읽고 검증한 다음, 다음 정각 파이프라인 실행까지 지켜봐줘
```

## 7. 검증 결과 (2026-07-26 15:46 KST)

부팅 15:44:05 → JVM 기동 15:46:15 (130초) → Spring 기동 완료 15:46:21. **10개 항목 전부 합격.**

| # | 항목 | 실측값 |
|---|---|---|
| 1 | 서비스 상태 | `Running` |
| 2 | 시작 유형 | `Auto` + `DelayedAutoStart=1` |
| 3 | 서비스 계정 | `LocalSystem` |
| 4 | JVM 세션 ID | `0` (검증 셸은 `1`) |
| 5 | 부모 프로세스 | `nssm.exe` |
| 6 | 부팅→기동 | 130초 |
| 7 | postgres / cloudflared | 둘 다 `Running` |
| 8 | 헬스 | `{"status":"UP","dbConnected":true,"reasons":[]}` |
| 9 | 도메인 | `200` |
| 10 | 로그 | `Started EconomicBriefingApplication` + `[Scheduler] ENABLED`, 부팅 이후 `ERROR`/`Exception` 없음 |

`sc qc EconomicBriefing`의 `DEPENDENCIES : postgresql-x64-17`도 확인했고,
postgres 뒤에 기동해 `dbConnected: true`로 DB 연결에 성공했습니다.

**아무도 시작시키지 않았고, 로그인과 무관하게(세션 0), postgres 뒤에 자동으로 떴습니다.**

## 8. 미해결 항목 (재부팅과 무관)

- **UptimeRobot 모니터 미등록** — `docs/MONITORING.md` 참고, 사용자 계정 필요
- **`.env` 개행 혼합** — PowerShell은 정상 파싱하지만 LF만 인식하는 도구로는 `ADMIN_TOKEN`이 깨져 보임. 동작에는 문제 없음
- **연합뉴스 RSS 수집 실패** — 소스 1개가 상시 실패 (`Failed to collect from 연합뉴스`)
- **`.idea/vcs.xml`** — 세션 시작 전부터 수정된 상태로 커밋에서 제외됨
