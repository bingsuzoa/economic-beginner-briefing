# 모니터링

서버는 개인 PC에서 Windows 서비스로 돌고 Cloudflare Tunnel로 공개됩니다.
이 구성에서 가장 흔한 장애는 **PC가 꺼지거나 네트워크가 끊기는 것**이고, 그때는 로컬에서
돌리는 감시 스크립트도 같이 죽습니다. 그래서 감시는 반드시 **외부**에 두어야 합니다.

## 감시 대상 엔드포인트

```
https://economic-beginner.com/api/health/briefing
```

| 항목 | 값 |
|---|---|
| 인증 | 불필요 |
| 정상 | `200` + `{"status":"UP", ...}` |
| 이상 | `503` + `{"status":"DOWN", "reasons":[...]}` |
| Cloudflare 캐싱 | 없음 (`cf-cache-status: DYNAMIC`, `Cache-Control: no-store`) |

캐싱되지 않으므로 매 요청이 오리진까지 도달합니다. 엣지에 걸린 오래된 200을 보고
정상이라고 착각할 일은 없습니다.

`503`이 되는 조건 (`BriefingHealthController`):

- 데이터베이스 연결 실패
- cron 표현식이 잘못됨
- `briefing.health.max-success-age`(기본 `3h`) 안에 성공한 실행이 없음
  — 스케줄러가 매시 정각이므로 **2회 연속 실패해야** DOWN이 됩니다. 일시적인 429는 알림을 만들지 않습니다.

## UptimeRobot 설정

무료 플랜으로 충분합니다 (5분 간격, 50개 모니터).

1. <https://uptimerobot.com> 가입 → **+ New monitor**
2. 아래대로 입력합니다.

   | 필드 | 값 |
   |---|---|
   | Monitor Type | `HTTP(s)` |
   | Friendly Name | `Economic Briefing` |
   | URL | `https://economic-beginner.com/api/health/briefing` |
   | Monitoring Interval | `5 minutes` |

3. **Advanced** 에서 아래를 켭니다.

   - *Monitor Timeout*: `30 seconds` — 파이프라인 실행 중에는 응답이 느려질 수 있습니다.
   - *Alert When Down For*: `2` 회 — 재시작 순간(약 5초)에 알림이 오지 않게 합니다.
     서비스는 5초 뒤 자동 재시작되므로 1회 실패는 대부분 자가 복구됩니다.

4. **Alert Contacts** 에서 이메일을 선택합니다. 복구 알림(`Up`)도 함께 켜 두세요.

### 두 번째 모니터 (선택)

메인 페이지도 따로 감시하면 백엔드는 살아 있는데 프론트 정적 파일이 빠진 상태를 잡습니다.

| 필드 | 값 |
|---|---|
| URL | `https://economic-beginner.com` |
| Keyword Type | `exists` |
| Keyword | 페이지에 항상 있는 문자열 |

`frontend/dist`가 없으면 이 모니터만 실패합니다 (헬스 엔드포인트는 정상 200).

## Cloudflare 관련 주의

Bot Fight Mode나 공격적인 WAF 규칙이 켜져 있으면 UptimeRobot 요청이 차단되어
**멀쩡한 서버를 DOWN으로 오탐**할 수 있습니다. 알림이 계속 오는데 브라우저로는 잘 열린다면
이걸 먼저 의심하세요.

해결: Cloudflare 대시보드 → **Security → WAF → Custom rules** 에서

```
(http.request.uri.path eq "/api/health/briefing")
```

에 대해 *Skip → All remaining custom rules* 규칙을 추가합니다.

## 알림을 받았을 때

```powershell
Get-Service EconomicBriefing                                  # 서비스가 살아 있나
Get-Service cloudflared, postgresql-x64-17                    # 의존 서비스는
Get-Content logs\service-stdout.log -Tail 50                  # 최근 로그
Invoke-WebRequest http://localhost:3000/api/health/briefing -UseBasicParsing | Select -Expand Content
```

로컬은 정상인데 도메인만 실패하면 터널 문제입니다 (`Restart-Service cloudflared`).
`reasons`에 `no successful run in ...`만 있으면 서버는 살아 있고 파이프라인만 밀린 상태이므로,
`logs\service-stdout.log`에서 해당 시각의 실패 원인을 확인하세요.
