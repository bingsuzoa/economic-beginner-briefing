# QA Test Cases - Part 3

Project: Economic Beginner Briefing

Version: 1.0

Document: Part 3

Test Cases: TC-081 ~ TC-120

Status: Ready

---

# 목적

본 문서는 Production 배포 전 최종 검증을 위한 QA Test Case를 정의한다.

검증 대상

- Performance
- Security
- Exception Handling
- Recovery
- Regression
- Production Readiness

모든 테스트를 통과해야 Release Approval이 가능하다.

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

# Performance Test

---

## TC-081 Pipeline 실행 시간

### 목적

Pipeline 전체 실행 시간을 측정한다.

### 기대 결과

허용 시간 내 완료

---

## TC-082 News 100건 처리

### 목적

대량 뉴스 처리

### 기대 결과

모든 뉴스 처리 완료

---

## TC-083 AI 처리 시간

### 기대 결과

허용 시간 내 완료

---

## TC-084 Notion Publish 시간

### 기대 결과

Timeout 없음

---

## TC-085 Database Insert 성능

### 기대 결과

Insert 지연 없음

---

## TC-086 Dashboard 응답 속도

### 기대 결과

2초 이내

---

## TC-087 API 응답 속도

### 기대 결과

평균 응답시간 허용 범위

---

## TC-088 메모리 사용량

### 기대 결과

Memory Leak 없음

---

## TC-089 CPU 사용량

### 기대 결과

비정상적인 사용량 없음

---

## TC-090 장시간 실행

### 목적

연속 실행 안정성 확인

### 기대 결과

오류 없이 정상 동작

---

# Security Test

---

## TC-091 Admin 인증

### 기대 결과

비인증 사용자는 접근 불가

---

## TC-092 Unauthorized API

### 기대 결과

401 또는 403 반환

---

## TC-093 Secret 노출 여부

### 확인 대상

- API Key
- Database URL
- Token

### 기대 결과

로그 및 응답에 노출되지 않음

---

## TC-094 Error Message

### 목적

민감한 Stack Trace 확인

### 기대 결과

사용자에게 노출되지 않음

---

## TC-095 SQL Injection

### 목적

기본 SQL Injection 시도

### 기대 결과

정상 차단

---

## TC-096 XSS

### 목적

스크립트 입력

### 기대 결과

실행되지 않음

---

## TC-097 CORS

### 기대 결과

허용 Origin만 접근 가능

---

## TC-098 Environment File

### 기대 결과

.env 외부 접근 불가

---

## TC-099 Log Security

### 기대 결과

민감정보 기록되지 않음

---

## TC-100 HTTPS 확인

### 기대 결과

운영 환경 HTTPS 정상

---

# Exception Test

---

## TC-101 Database Down

### 목적

DB 연결 종료

### 기대 결과

Application 종료되지 않음

---

## TC-102 OpenAI 장애

### 기대 결과

Pipeline 실패 기록

서비스 유지

---

## TC-103 Notion 장애

### 기대 결과

Error 저장

---

## TC-104 News API 장애

### 기대 결과

Retry 또는 Error 처리

---

## TC-105 Timeout

### 기대 결과

무한 대기 없음

---

## TC-106 Invalid JSON

### 기대 결과

예외 처리

---

## TC-107 Empty Response

### 기대 결과

Crash 없음

---

## TC-108 Network Error

### 기대 결과

Retry 또는 Error 처리

---

## TC-109 Disk Full

### 기대 결과

로그 기록

서비스 종료 방지

---

## TC-110 Unexpected Exception

### 기대 결과

Global Exception 처리

---

# Recovery Test

---

## TC-111 Pipeline 재실행

### 기대 결과

정상 수행

---

## TC-112 서버 재시작

### 기대 결과

서비스 자동 복구

---

## TC-113 Scheduler 복구

### 기대 결과

예약 유지

---

## TC-114 Database 재연결

### 기대 결과

자동 재연결

---

## TC-115 실패 후 재실행

### 기대 결과

정상 완료

---

# Regression Test

---

## TC-116 기존 Pipeline

### 기대 결과

기존 기능 정상

---

## TC-117 Dashboard

### 기대 결과

기존 기능 정상

---

## TC-118 API

### 기대 결과

모든 API 정상

---

## TC-119 Database

### 기대 결과

기존 데이터 정상

Migration 영향 없음

---

# Production Readiness

---

## TC-120 최종 Release 검증

### 확인 항목

- Build 성공
- Test 성공
- Pipeline 성공
- Dashboard 정상
- API 정상
- Database 정상
- Scheduler 정상
- GitHub Actions 정상
- Security 확인
- Regression 완료

### 기대 결과

Production Ready

---

# Part3 완료 기준

TC-081 ~ TC-120

PASS Rate

100%

Critical

0

Major

0

Minor

허용

---

# 최종 QA 판정 기준

다음 조건을 모두 만족해야 Release 가능하다.

- 전체 Test Case 수행 완료
- Critical Bug = 0
- Major Bug = 0
- Pipeline 정상
- Dashboard 정상
- Database 정상
- Scheduler 정상
- GitHub Actions 정상
- Security Issue 없음
- Regression 완료
- Production 환경에서 정상 운영 가능

최종 판정은 아래 둘 중 하나만 선택한다.

✅ Production Ready

또는

❌ Release Hold