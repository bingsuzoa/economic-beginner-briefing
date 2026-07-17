# QA Test Cases - Part 1

Project: Economic Beginner Briefing

Version: 1.0

Document: Part 1

Test Cases: TC-001 ~ TC-040

Status: Ready

---

# 목적

본 문서는 Pipeline 및 핵심 기능에 대한 QA Test Case를 정의한다.

모든 테스트는 실제 환경과 최대한 동일한 환경에서 수행한다.

모든 테스트는 PASS 또는 FAIL을 기록한다.

FAIL 발생 시 원인 분석 → 수정 → 재검증을 수행한다.

---

# 결과 기록 양식

모든 테스트는 아래 형식으로 기록한다.

Result

PASS / FAIL

Execution Time

Environment

Evidence

Bug Severity

Remark

---

# Pipeline

---

## TC-001 프로젝트 Build

### 목적

프로젝트가 정상적으로 Build 되는지 확인한다.

### 사전조건

- Repository Clone 완료
- Environment Variables 설정 완료

### 수행 절차

1. 의존성 설치
2. Build 실행

### 기대 결과

- Build 성공
- Error 없음

### PASS 기준

- Exit Code = 0

---

## TC-002 프로젝트 실행

### 목적

Application이 정상적으로 실행되는지 확인한다.

### 수행 절차

1. 서버 실행

### 기대 결과

- Server Started 출력
- Runtime Error 없음

---

## TC-003 환경 변수 검증

### 목적

필수 Environment Variable이 모두 존재하는지 확인한다.

확인 대상

- DATABASE_URL
- OPENAI_API_KEY
- NOTION_API_KEY
- NEWS_API_KEY

### 기대 결과

누락 없음

---

## TC-004 Database Connection

### 목적

Database 연결을 확인한다.

### 기대 결과

- Connection Success
- Timeout 없음

---

## TC-005 Migration

### 목적

Migration이 정상 적용되는지 확인한다.

### 기대 결과

- 실패 없음
- Duplicate 없음

---

# News Collector

---

## TC-006 News API 연결

### 목적

뉴스 API 연결 확인

### 기대 결과

200 OK

---

## TC-007 뉴스 수집

### 목적

뉴스가 실제 수집되는지 확인

### 기대 결과

1건 이상 저장

---

## TC-008 중복 뉴스 제거

### 목적

Duplicate Filter 확인

### 수행 절차

동일 뉴스 재실행

### 기대 결과

중복 저장되지 않음

---

## TC-009 뉴스 필드 검증

확인 항목

- title
- url
- publishedAt
- source

기대 결과

NULL 없음

---

## TC-010 잘못된 응답 처리

목적

News API 장애 대응

기대 결과

Application 종료되지 않음

Error Log 저장

---

# AI Analysis

---

## TC-011 OpenAI 연결

기대 결과

정상 응답

---

## TC-012 AI 분석 성공

기대 결과

Summary 생성

---

## TC-013 빈 뉴스 처리

입력

Empty Article

기대 결과

예외 없이 Skip

---

## TC-014 긴 뉴스 처리

입력

매우 긴 기사

기대 결과

Timeout 없이 처리

---

## TC-015 AI 실패 처리

강제

API Key 제거

기대 결과

Pipeline 중단 없이 Error 저장

---

## TC-016 AI 결과 저장

기대 결과

Database 저장 성공

---

## TC-017 AI 결과 길이 검증

기대 결과

Empty Summary 없음

---

## TC-018 특수문자 포함 기사

기대 결과

Encoding 오류 없음

---

## TC-019 한글 기사

기대 결과

정상 분석

---

## TC-020 영어 기사

기대 결과

정상 분석

---

# Notion Publisher

---

## TC-021 Notion 연결

기대 결과

API Success

---

## TC-022 Page 생성

기대 결과

새 페이지 생성

---

## TC-023 제목 저장

기대 결과

Title 일치

---

## TC-024 Summary 저장

기대 결과

AI Summary 저장

---

## TC-025 URL 저장

기대 결과

원본 URL 저장

---

## TC-026 Category 저장

기대 결과

Category 저장

---

## TC-027 중복 발행 방지

기대 결과

동일 기사 재발행 없음

---

## TC-028 Publish 실패 처리

기대 결과

Pipeline 종료되지 않음

Error 저장

---

## TC-029 Publish 상태 저장

기대 결과

published 상태 변경

---

## TC-030 Published URL 저장

기대 결과

Database 저장

---

# Pipeline Integration

---

## TC-031 Pipeline 전체 실행

기대 결과

Collector

↓

AI

↓

Notion

순서대로 실행

---

## TC-032 실행 이력 저장

기대 결과

pipeline_runs 저장

---

## TC-033 로그 저장

기대 결과

pipeline_logs 저장

---

## TC-034 Item 저장

기대 결과

pipeline_items 저장

---

## TC-035 실행 시간 기록

기대 결과

Duration 저장

---

## TC-036 실패 상태 저장

기대 결과

FAILED 저장

---

## TC-037 성공 상태 저장

기대 결과

SUCCESS 저장

---

## TC-038 Pipeline 재실행

기대 결과

정상 수행

---

## TC-039 동시 실행 방지

기대 결과

중복 실행 차단

---

## TC-040 종료 후 Resource 확인

기대 결과

Memory Leak 없음

Connection 반환 완료

---

# Part1 완료 기준

모든 Test Case

TC-001 ~ TC-040

PASS Rate

100%

Critical

0

Major

0

Minor

허용