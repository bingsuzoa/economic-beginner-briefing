# Economic Beginner Briefing
# 운영 이력 영속화 및 Admin Dashboard 구축 기획서

## 1. 프로젝트 배경

현재 프로젝트는 아래 자동화 Pipeline을 수행하는 백엔드 서비스이다.

```text
Scheduler 또는 GitHub Actions
        ↓
News Collector
        ↓
중복 뉴스 제거
        ↓
AI Analysis
        ↓
Notion Publisher
        ↓
실행 종료
```

현재 주요 기능은 구현되어 있지만, 운영자가 다음 내용을 확인할 수 있는 구조가 부족하다.

- Pipeline이 실제로 실행되었는지
- 실행이 성공했는지 실패했는지
- 몇 건의 뉴스가 수집되었는지
- 어떤 뉴스의 AI 분석이 실패했는지
- 어떤 뉴스가 Notion에 저장되었는지
- 실패 원인이 무엇인지
- 다음 실행이 언제인지
- 필요할 때 수동으로 Pipeline을 실행할 수 있는지

현재 콘솔 로그만으로 운영할 경우 다음 문제가 발생한다.

- 프로세스가 종료되면 실행 상태를 확인하기 어렵다.
- GitHub Actions에서 실행된 결과를 서비스 화면에서 확인할 수 없다.
- 과거 실행 결과를 비교할 수 없다.
- 뉴스별 처리 결과를 추적하기 어렵다.
- 운영자가 터미널 없이 서비스를 관리할 수 없다.

따라서 이번 작업은 단순한 관리자 화면 구현이 아니다.

이번 작업의 목표는 다음 세 가지를 한 번에 구축하는 것이다.

```text
1. Pipeline 운영 이력 DB 영속화
2. 운영 이력 조회 및 제어 API
3. 운영자가 사용하는 Admin Dashboard
```

## 2. 최종 목표

운영자는 브라우저에서 Admin Dashboard에 접속해 다음 작업을 수행할 수 있어야 한다.

- 현재 서비스 상태 확인
- 최근 Pipeline 실행 결과 확인
- 과거 실행 이력 조회
- 특정 실행의 상세 로그 확인
- 수집된 뉴스 확인
- 원문과 AI 분석 결과 비교
- Notion 저장 결과 확인
- 실패한 뉴스와 원인 확인
- Pipeline 수동 실행
- Scheduler 상태 확인
- 중복 실행 방지 상태 확인

관리자 화면의 기본 URL은 다음과 같이 구성한다.

```text
/admin
```

단, 현재 프로젝트의 라우팅 또는 서버 구조와 충돌한다면 기존 구조에 맞는 경로를 선택할 수 있다.

## 3. 핵심 설계 원칙

### 3.1 기존 Pipeline 재사용

Admin Dashboard의 수동 실행 기능은 반드시 기존 Pipeline 함수를 호출해야 한다.

다음 로직을 별도로 복제하거나 다시 작성하면 안 된다.

- 뉴스 수집
- 중복 제거
- AI 분석
- Notion 저장
- Scheduler 실행 로직

수동 실행, Scheduler 실행, GitHub Actions 실행은 모두 동일한 Pipeline 진입점을 재사용해야 한다.

예시:

```text
runPipeline({ triggerType: "MANUAL" })
runPipeline({ triggerType: "SCHEDULER" })
runPipeline({ triggerType: "GITHUB_ACTIONS" })
```

### 3.2 운영 데이터와 개발 로그 분리

모든 콘솔 로그를 DB에 저장하는 방식으로 구현하지 않는다.

DB에는 운영자가 확인해야 하는 구조화된 운영 기록을 저장한다.

DB에 저장할 데이터:

- 실행 시작 및 종료
- 실행 상태
- 실행 방식
- 단계별 처리 결과
- 뉴스별 처리 상태
- 오류 메시지
- 처리 건수
- Notion 저장 URL
- 실행 소요시간

콘솔 또는 파일에만 남겨도 되는 데이터:

- 함수 진입 로그
- 변수 전체 출력
- HTTP 객체 전체
- 라이브러리 내부 디버그 로그
- 불필요하게 긴 Stack Trace 전체
- 민감정보가 포함될 가능성이 있는 요청 및 응답 원문

단, 운영 장애 분석에 필요한 오류 정보는 민감정보를 제거한 후 DB에 저장한다.

### 3.3 민감정보 저장 금지

다음 값은 DB, 로그, 화면에 절대 노출하지 않는다.

- API Key
- Access Token
- Authorization Header
- Notion Secret
- OpenAI API Key
- 사용자 비밀번호
- 전체 환경변수
- 외부 API 요청의 인증정보

### 3.4 GitHub Actions와 상시 서버 모두 고려

이 프로젝트는 주간 배치 실행을 목적으로 한다.

실행 주체는 다음과 같이 달라질 수 있다.

- 로컬 수동 실행
- 내부 Scheduler
- GitHub Actions
- Admin Dashboard 수동 실행

프로세스가 실행 후 종료되더라도 과거 실행 결과를 확인할 수 있도록 운영 이력은 반드시 DB에 저장해야 한다.

## 4. 선행 조사

구현 전에 현재 프로젝트를 먼저 분석한다.

- 현재 기술 스택
- 서버 프레임워크
- DB 사용 여부
- ORM 또는 Query Builder 사용 여부
- Migration 도구 사용 여부
- 기존 뉴스 저장 구조
- 기존 AI 분석 결과 저장 구조
- 기존 Notion 저장 정보
- Pipeline 진입 함수
- Scheduler 진입 함수
- GitHub Actions 실행 명령
- 현재 로그 처리 방식
- package.json scripts
- 테스트 구조
- 환경변수 구조

기존 DB 테이블이나 저장 구조가 있다면 중복 테이블을 새로 만들지 말고 최대한 재사용한다.

## 5. 권장 데이터 모델

아래 스키마는 요구사항 기준의 권장안이다. 실제 프로젝트의 DB, ORM, 명명 규칙에 맞게 조정할 수 있다.

### 5.1 pipeline_runs

Pipeline 한 번의 실행을 나타낸다. 한 번 실행할 때 한 행이 생성된다.

| 컬럼 | 설명 |
|---|---|
| id | 실행 ID |
| status | RUNNING, SUCCESS, PARTIAL_SUCCESS, FAILED |
| trigger_type | MANUAL, SCHEDULER, GITHUB_ACTIONS, LOCAL |
| started_at | 실행 시작 시각 |
| finished_at | 실행 종료 시각 |
| duration_ms | 총 소요시간 |
| current_step | 현재 또는 마지막 단계 |
| collected_count | 수집된 뉴스 수 |
| duplicate_count | 중복으로 제외된 뉴스 수 |
| analysis_success_count | AI 분석 성공 수 |
| analysis_failure_count | AI 분석 실패 수 |
| publish_success_count | Notion 저장 성공 수 |
| publish_failure_count | Notion 저장 실패 수 |
| total_failure_count | 전체 실패 수 |
| error_code | 대표 오류 코드 |
| error_message | 대표 오류 메시지 |
| created_at | 생성 시각 |
| updated_at | 수정 시각 |

상태 정의:

```text
RUNNING: 실행 중
SUCCESS: 모든 대상이 성공적으로 처리됨
PARTIAL_SUCCESS: 일부 뉴스 처리에 실패했지만 Pipeline 전체는 종료됨
FAILED: Pipeline 자체가 중단되거나 핵심 단계가 실패함
```

### 5.2 pipeline_logs

실행별 주요 단계와 운영 로그를 저장한다.

| 컬럼 | 설명 |
|---|---|
| id | 로그 ID |
| run_id | pipeline_runs 참조 |
| level | INFO, WARN, ERROR |
| step | INIT, COLLECT, DEDUPLICATE, ANALYZE, PUBLISH, COMPLETE |
| event_code | 구조화된 이벤트 코드 |
| message | 운영자가 읽을 수 있는 메시지 |
| metadata_json | 추가 정보 |
| created_at | 로그 발생 시각 |

metadata에는 API Key, Token, Authorization 정보가 포함되면 안 된다.

### 5.3 pipeline_items

Pipeline 실행 중 처리된 뉴스 단위의 결과를 저장한다.

기존 뉴스 테이블이 이미 있다면 별도 테이블을 생성하지 않고 연관 테이블이나 처리 이력 테이블로 구성할 수 있다.

| 컬럼 | 설명 |
|---|---|
| id | 처리 항목 ID |
| run_id | pipeline_runs 참조 |
| news_id | 기존 뉴스 테이블 참조, 존재할 경우 |
| article_url | 원문 URL |
| normalized_url | 중복 판별용 URL |
| source | 뉴스 출처 |
| original_title | 원문 제목 |
| original_summary | 원문 요약 또는 본문 일부 |
| published_at | 뉴스 발행일 |
| category | 분류 |
| duplicate_status | UNIQUE, DUPLICATE |
| analysis_status | PENDING, SUCCESS, FAILED, SKIPPED |
| analysis_result_json | AI 분석 결과 |
| analysis_error_message | AI 오류 |
| publish_status | PENDING, SUCCESS, FAILED, SKIPPED |
| notion_page_id | Notion 페이지 ID |
| notion_page_url | Notion URL |
| publish_error_message | Notion 오류 |
| created_at | 생성 시각 |
| updated_at | 수정 시각 |

AI 결과가 이미 별도 테이블에 저장되고 있다면 JSON 중복 저장을 피하고 참조값을 사용한다.

### 5.4 scheduler_state

상시 서버 Scheduler를 사용하는 경우에만 필요하다. GitHub Actions만 사용하는 경우에는 만들지 않아도 된다.

| 컬럼 | 설명 |
|---|---|
| id | 상태 ID |
| enabled | Scheduler 활성 여부 |
| cron_expression | Cron 표현식 |
| timezone | 타임존 |
| next_run_at | 다음 실행 예정 |
| last_started_at | 마지막 시작 시각 |
| updated_at | 수정 시각 |

## 6. Pipeline 운영 이력 기록 방식

### 6.1 실행 시작

Pipeline 시작 직후 `pipeline_runs`에 다음 값으로 행을 생성한다.

```text
status = RUNNING
trigger_type = 실행 주체
started_at = 현재 시각
current_step = INIT
```

생성된 `run_id`는 Pipeline 전체에서 공유한다.

### 6.2 단계 진행

각 단계가 시작되거나 완료될 때 다음을 수행한다.

- `pipeline_runs.current_step` 업데이트
- `pipeline_logs`에 주요 이벤트 저장
- 처리 건수 업데이트
- 뉴스별 상태 업데이트

### 6.3 뉴스별 처리

```text
뉴스 수집
→ analysis_status = PENDING
→ publish_status = PENDING

AI 분석 성공
→ analysis_status = SUCCESS
→ AI 결과 저장

AI 분석 실패
→ analysis_status = FAILED
→ 오류 메시지 저장
→ publish_status = SKIPPED

Notion 저장 성공
→ publish_status = SUCCESS
→ notion_page_url 저장

Notion 저장 실패
→ publish_status = FAILED
→ 오류 메시지 저장
```

### 6.4 실행 종료

Pipeline 종료 시 다음을 업데이트한다.

- finished_at
- duration_ms
- 최종 처리 건수
- 최종 status
- 대표 오류 정보
- current_step = COMPLETE

### 6.5 예상하지 못한 예외

최상위 Pipeline 함수에 예외 처리 구간을 둔다.

예외가 발생하면 가능한 범위에서 다음을 수행한다.

- 현재 run을 FAILED로 변경
- 종료 시각 저장
- 오류 메시지 저장
- ERROR 로그 저장
- 호출자에게 실패 상태 반환
- 프로세스 종료 코드 설정

DB 장애로 실패 상태조차 저장할 수 없는 경우에는 콘솔에 명확한 오류를 남긴다.

## 7. 중복 실행 방지

Pipeline이 이미 실행 중이면 두 번째 실행을 허용하지 않는다.

수동 실행, Scheduler, GitHub Actions 등 실행 주체가 달라도 중복 방지가 적용되어야 한다.

단순 메모리 Boolean만 사용하지 말고 운영 구조에 맞게 다음 중 적절한 방식을 사용한다.

- DB Lock
- Advisory Lock
- 분산 Lock
- RUNNING 상태 + 만료시간
- GitHub Actions concurrency
- 단일 프로세스 내부 Lock

오래된 RUNNING 상태가 무한히 남는 문제도 처리한다.

## 8. Admin API 설계

기존 프로젝트의 API 응답 규칙이 있다면 해당 규칙을 따른다.

### 상태 조회

```http
GET /api/admin/status
```

반환 정보:

- 서비스 상태
- 현재 실행 중 여부
- 현재 실행 ID
- 현재 단계
- 마지막 실행 정보
- 다음 Scheduler 실행 시각
- Scheduler 활성 여부
- DB 연결 상태
- 최근 오류 요약

### 실행 이력 목록

```http
GET /api/admin/runs
```

Query Parameter:

```text
page, size, status, triggerType, from, to
```

Pagination을 적용한다.

### 실행 상세

```http
GET /api/admin/runs/:runId
```

### 실행 로그 조회

```http
GET /api/admin/runs/:runId/logs
```

### 실행별 뉴스 조회

```http
GET /api/admin/runs/:runId/items
```

### 뉴스 상세 조회

```http
GET /api/admin/items/:itemId
```

### 수동 실행

```http
POST /api/admin/runs
```

동작:

- 기존 Pipeline 함수를 즉시 호출
- trigger_type = MANUAL
- 이미 실행 중이면 거절
- 장시간 HTTP 요청을 유지하지 않도록 비동기 실행 구조 고려

### Scheduler 조회 및 제어

```http
GET /api/admin/scheduler
POST /api/admin/scheduler/start
POST /api/admin/scheduler/stop
```

단, 상시 서버 Scheduler를 실제로 사용하는 경우에만 제어 API를 제공한다.

GitHub Actions만 사용하는 경우에는 가짜 Scheduler 제어 기능을 만들지 않는다.

## 9. 인증 및 접근 제어

Admin Dashboard는 외부에 공개되는 일반 사용자 화면이 아니다.

운영 환경에서는 반드시 접근 제어를 적용한다.

현재 인증 체계가 없다면 최소한 다음 중 하나를 구현한다.

- 관리자 계정 로그인
- HTTP Basic Auth
- Reverse Proxy 인증
- 환경변수 기반 Admin Token
- 사설 네트워크 제한

다음 API는 반드시 보호한다.

- 수동 Pipeline 실행
- Scheduler 시작 및 중지
- 로그 조회
- 뉴스 상세 조회
- Notion URL 조회

## 10. Admin Dashboard 화면 기획

### 10.1 디자인 방향

- 운영자용 화면
- 데스크톱 우선
- 모바일에서도 최소한 조회 가능
- 한글 UI
- 화려함보다 가독성과 상태 확인 우선
- 로딩, 빈 데이터, 오류 상태 구현
- 가짜 데이터 사용 금지
- 실제 Admin API와 연결

상태 표시:

```text
SUCCESS: 초록색
PARTIAL_SUCCESS: 주황색
RUNNING: 파란색
FAILED: 빨간색
대기 및 미실행: 회색
```

색상만 사용하지 말고 텍스트 또는 아이콘을 함께 표시한다.

### 10.2 메인 Dashboard

URL:

```text
/admin
```

상단 요약:

- 현재 서비스 상태
- 현재 Pipeline 실행 여부
- 마지막 실행 시각
- 마지막 실행 상태
- 다음 실행 예정 시각
- 뉴스 수집 수
- AI 분석 성공 및 실패 수
- Notion 저장 성공 및 실패 수

버튼:

- 지금 실행
- 새로고침
- 현재 실행 상세 보기

### 10.3 최근 실행 이력

컬럼:

- 실행 ID
- 시작 시각
- 실행 방식
- 상태
- 소요시간
- 수집
- 중복 제외
- AI 성공
- AI 실패
- Notion 성공
- Notion 실패

기능:

- Pagination
- 상태 필터
- 기간 필터
- 실행 방식 필터
- 행 클릭 시 상세 화면 이동

### 10.4 실행 상세 화면

URL 예시:

```text
/admin/runs/:runId
```

표시 정보:

- 실행 상태
- 실행 주체
- 시작 및 종료 시각
- 소요시간
- 현재 또는 마지막 단계
- 처리 건수
- 대표 오류
- 단계별 Timeline
- 실행 로그
- 처리 뉴스 목록

### 10.5 뉴스 처리 목록

컬럼:

- 제목
- 출처
- 발행일
- 중복 여부
- AI 상태
- Notion 상태
- 오류 유무

필터:

- AI 실패만 보기
- Notion 실패만 보기
- 중복 뉴스만 보기
- 출처별 보기

### 10.6 뉴스 상세 화면

URL 예시:

```text
/admin/items/:itemId
```

원문 영역:

- 원문 제목
- 출처
- 발행일
- 원문 URL
- 저장된 원문 요약 또는 본문 일부

AI 분석 영역:

- AI 생성 제목
- 요약
- 핵심 내용
- 중요도
- 카테고리
- 현재 AI 결과 필드

Notion 영역:

- 저장 상태
- 저장 시각
- Notion Page ID
- Notion URL
- 저장 실패 원인

AI 결과 JSON을 그대로 덤프하지 말고 사람이 읽기 쉬운 UI로 표현한다.

### 10.7 로그 화면

기능:

- 최신순 및 시간순 정렬
- 실행 ID 필터
- Level 필터
- Step 필터
- 오류만 보기
- 자동 새로고침
- 긴 metadata 접기 및 펼치기

초기 버전에서는 3~5초 Polling으로 현재 실행 상태를 갱신해도 된다.

## 11. 오류 처리

일관된 오류 응답 구조를 사용한다.

```json
{
  "success": false,
  "code": "RUN_NOT_FOUND",
  "message": "실행 기록을 찾을 수 없습니다."
}
```

화면에서는 다음 상태를 각각 처리한다.

- DB 연결 실패
- Admin API 호출 실패
- 실행 이력 없음
- 현재 실행 없음
- Pipeline 수동 실행 실패
- 권한 없음
- Notion URL 없음
- AI 분석 결과 없음

뉴스 일부가 실패해도 성공한 항목은 정상 표시해야 한다.

## 12. 성능 및 데이터 보관

아래 인덱스를 고려한다.

- pipeline_runs.started_at
- pipeline_runs.status
- pipeline_logs.run_id
- pipeline_logs.created_at
- pipeline_items.run_id
- pipeline_items.normalized_url
- pipeline_items.notion_page_id

로그 보관 정책을 문서화한다.

예시:

```text
실행 이력: 장기 보관
운영 로그: 최근 90일
상세 metadata: 최근 30일
```

자동 삭제는 초기 범위에서 제외할 수 있다.

## 13. Migration 및 초기화

DB 변경은 반드시 Migration으로 관리한다.

다음을 제공한다.

- Migration 파일
- Rollback 가능 여부
- 로컬 초기화 방법
- 테스트 DB 적용 방법
- 운영 DB 적용 방법

## 14. 테스트 요구사항

외부 API는 테스트에서 직접 호출하지 않는다.

OpenAI, Notion, 뉴스 API는 Mock 또는 Fake로 대체한다.

### 데이터 저장 테스트

- Pipeline 시작 시 run 생성
- 단계별 로그 저장
- 처리 건수 업데이트
- 성공 시 SUCCESS 저장
- 일부 실패 시 PARTIAL_SUCCESS 저장
- 전체 실패 시 FAILED 저장
- finished_at 및 duration_ms 저장
- 뉴스별 상태 저장
- Notion URL 저장
- 민감정보 미저장

### 중복 실행 방지 테스트

- 첫 번째 실행 중 두 번째 수동 실행 거절
- Scheduler와 수동 실행 충돌 방지
- 오래된 RUNNING 상태 처리
- Lock 해제 보장
- 예외 발생 후 Lock 정리

### Admin API 테스트

- 상태 조회
- 실행 목록 Pagination
- 실행 상세 조회
- 존재하지 않는 실행
- 로그 필터
- 뉴스 상태 필터
- 수동 실행 성공
- 수동 실행 중복 거절
- 인증되지 않은 요청 거절

### Dashboard 테스트

- 로딩 상태
- 실행 이력 없음
- 성공 상태 표시
- 부분 성공 상태 표시
- 실패 상태 표시
- 수동 실행 버튼
- 중복 실행 안내
- API 오류 표시
- 뉴스 상세 표시
- Notion 링크 새 창 열기

### 기존 기능 회귀 테스트

Admin 기능 추가 후에도 기존 테스트가 모두 통과해야 한다.

## 15. 구현 순서

1. 프로젝트 분석
2. 데이터 모델 및 Migration
3. Pipeline 기록 연결
4. 중복 실행 방지
5. Admin API
6. Admin Dashboard
7. 인증 및 보안
8. 테스트 및 문서화

## 16. 현재 범위에서 제외하는 기능

- 일반 사용자용 화면
- Email Publisher 구현
- 뉴스 원문 수정 기능
- AI 분석 결과 수동 편집
- Notion 재발행 버튼
- 실패 항목 개별 재처리
- 실시간 WebSocket 로그
- 복잡한 사용자 권한 체계
- 통계 차트 및 BI
- 로그 자동 삭제 배치
- 모바일 전용 디자인

## 17. 금지사항

- 기존 Pipeline 로직 복제
- Scheduler 중복 구현
- AI 분석 로직 임의 변경
- Notion Publisher 임의 변경
- 테스트에서 실제 외부 API 반복 호출
- API Key 하드코딩
- 로그에 인증정보 저장
- 서버 메모리에만 실행 이력 저장
- 가짜 Dashboard 데이터 사용
- 무인증 Admin API 운영 배포
- 검증 없이 대규모 프레임워크 교체
- Git 강제 Reset
- Push Force
- 기존 커밋 삭제
- 사용자 확인 없이 main 병합 또는 원격 Push

## 18. 완료 조건

- 실행 이력이 DB에 저장된다.
- 프로세스 재시작 후에도 과거 실행 이력을 조회할 수 있다.
- 실행별 단계 로그를 조회할 수 있다.
- 뉴스별 AI 및 Notion 처리 상태를 조회할 수 있다.
- 수동 실행이 기존 Pipeline을 재사용한다.
- 중복 실행이 방지된다.
- Admin API가 실제 DB 데이터를 반환한다.
- `/admin`에서 최근 실행 결과를 확인할 수 있다.
- 실행 상세 화면을 확인할 수 있다.
- 뉴스 원문과 AI 결과를 비교할 수 있다.
- Notion URL을 열 수 있다.
- 실패 원인을 화면에서 확인할 수 있다.
- Admin 접근 제어가 적용된다.
- 민감정보가 로그나 DB에 저장되지 않는다.
- Migration이 제공된다.
- 외부 API를 Mock 처리한 테스트가 통과한다.
- 기존 프로젝트 테스트가 모두 통과한다.
- README와 실제 명령어가 일치한다.
- git status가 clean하다.
- main에 병합 가능한 상태다.

## 19. Claude Code 작업 지시

현재 프로젝트를 직접 분석한 뒤 위 기획을 구현한다.

중요 지침:

1. 먼저 현재 구조를 조사하고 구현 계획을 제시한다.
2. 기존 DB와 테이블을 최대한 재사용한다.
3. 필요한 경우에만 새로운 테이블을 추가한다.
4. 기존 Pipeline 진입점을 재사용한다.
5. 운영 이력을 구조화된 데이터로 저장한다.
6. Admin API와 Dashboard는 실제 DB 데이터만 사용한다.
7. 상시 Scheduler와 GitHub Actions 중 실제 운영 구조를 정확히 구분한다.
8. 존재하지 않는 Scheduler 제어 기능을 가짜로 만들지 않는다.
9. 보안과 인증을 반드시 고려한다.
10. 구현 후 테스트, Migration, README를 함께 완성한다.

## 20. Git 작업 지침

새 브랜치에서 작업한다.

권장 브랜치:

```text
feature/admin-dashboard
```

작업 전 다음을 확인한다.

```text
git status
git branch
git worktree list
```

다음 명령은 사용하지 않는다.

```text
git reset --hard
git push --force
git rebase --onto
```

논리적으로 커밋을 분리한다.

```text
feat: persist pipeline run history
feat: add admin operation APIs
feat: add admin dashboard
test: cover pipeline operation history
docs: document admin operations
```

main 브랜치 병합과 원격 Push는 수행하지 않는다.

## 21. 최종 보고서 형식

작업 완료 후 다음 형식으로 보고한다.

1. 기존 구조 분석
2. 데이터 모델
3. Pipeline 변경
4. Admin API
5. Admin Dashboard
6. 테스트 결과
7. 실행 방법
8. 환경변수
9. 보안 검토
10. 남은 작업
11. 생성한 커밋 목록
12. 최종 git status
13. main 병합 가능 여부
