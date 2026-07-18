# QA Test Cases - Part 2

Project: Economic Beginner Briefing

Version: 1.0

Document: Part 2

Test Cases: TC-041 ~ TC-080

Status: Ready

---

# 목적

본 문서는 운영(Operations) 기능에 대한 QA Test Case를 정의한다.

검증 대상

- Admin Dashboard
- Admin API
- Scheduler
- GitHub Actions
- Logging
- Authentication
- Error Recovery

모든 테스트는 실제 운영 환경과 최대한 동일한 환경에서 수행한다.

---

# 결과 기록 양식

Result

PASS / FAIL

Execution Time

Environment

Evidence

Bug Severity

Remark

---

# Admin Dashboard

---

## TC-041 Dashboard 접근

### 목적

Dashboard 접근 가능 여부 확인

### 기대 결과

- 로그인 페이지 또는 Dashboard 표시
- 200 OK

---

## TC-042 Dashboard Status

### 목적

현재 Pipeline 상태 확인

### 기대 결과

- Running
- Idle
- Failed

상태가 정확하게 표시된다.

---

## TC-043 Pipeline 실행 이력

### 목적

History 목록 확인

### 기대 결과

최근 실행 이력이 시간순으로 표시된다.

---

## TC-044 Pipeline 상세 조회

### 목적

History Detail 확인

### 기대 결과

- 실행시간
- 상태
- 로그
- 실행 결과

모두 조회 가능

---

## TC-045 Dashboard Refresh

### 목적

새로고침 후 데이터 유지 확인

### 기대 결과

동일 데이터 표시

---

## TC-046 Manual Pipeline Run

### 목적

Dashboard에서 수동 실행

### 기대 결과

Pipeline 정상 시작

---

## TC-047 실행 중 UI

### 목적

Running 상태 표시

### 기대 결과

Loading 표시

중복 클릭 방지

---

## TC-048 완료 후 상태

### 목적

Pipeline 종료 확인

### 기대 결과

SUCCESS

또는

FAILED

표시

---

## TC-049 실행 시간 표시

### 기대 결과

Duration 표시

---

## TC-050 Dashboard Error 처리

### 목적

API 실패

### 기대 결과

Dashboard 종료되지 않음

Error Message 출력

---

# Admin API

---

## TC-051 Health Check API

### 기대 결과

200 OK

---

## TC-052 Pipeline Status API

### 기대 결과

현재 상태 반환

---

## TC-053 Pipeline History API

### 기대 결과

History 반환

---

## TC-054 Pipeline Detail API

### 기대 결과

상세 결과 반환

---

## TC-055 Manual Execute API

### 기대 결과

Pipeline 실행

---

## TC-056 Duplicate Execute

### 목적

동시 실행 요청

### 기대 결과

중복 실행 차단

---

## TC-057 Invalid Request

### 기대 결과

400 또는 적절한 오류

---

## TC-058 Exception Response

### 기대 결과

500 Error

JSON 응답

---

## TC-059 API Response Time

### 기대 결과

2초 이내

---

## TC-060 API Logging

### 기대 결과

Request Log 저장

---

# Scheduler

---

## TC-061 Scheduler 시작

### 기대 결과

자동 시작

---

## TC-062 Scheduler Job 등록

### 기대 결과

Job 등록 완료

---

## TC-063 예약 실행

### 기대 결과

예약 시간에 실행

---

## TC-064 실행 후 종료

### 기대 결과

정상 종료

---

## TC-065 중복 예약

### 기대 결과

중복 실행 없음

---

## TC-066 Scheduler Error

### 기대 결과

다음 스케줄 유지

---

## TC-067 Scheduler Log

### 기대 결과

실행 로그 저장

---

## TC-068 Scheduler 재시작

### 기대 결과

Job 유지

---

## TC-069 서버 재부팅

### 기대 결과

Scheduler 자동 복구

---

## TC-070 Timezone 확인

### 기대 결과

설정한 Timezone 기준 실행

---

# GitHub Actions

---

## TC-071 Workflow 실행

### 기대 결과

Workflow Success

---

## TC-072 Build

### 기대 결과

Build Success

---

## TC-073 Test

### 기대 결과

모든 Test 통과

---

## TC-074 Secret

### 기대 결과

Secrets 정상 사용

---

## TC-075 Workflow Log

### 기대 결과

Error 없음

---

## TC-076 Cron 실행

### 기대 결과

예약 실행 성공

---

## TC-077 Failure Retry

### 기대 결과

Retry 정상 수행

---

## TC-078 Workflow Timeout

### 기대 결과

Timeout 발생 없음

---

## TC-079 Cache

### 기대 결과

Dependency Cache 정상 사용

---

## TC-080 Workflow 종료

### 기대 결과

SUCCESS

---

# Logging

(운영 중 반드시 확인)

Dashboard

↓

API

↓

Scheduler

↓

Pipeline

↓

Notion

↓

Database

모든 로그가 시간순으로 기록되어야 한다.

---

# Part2 완료 기준

TC-041 ~ TC-080

PASS Rate

100%

Critical

0

Major

0

Minor

허용

모든 운영 기능이 정상 동작해야 한다.