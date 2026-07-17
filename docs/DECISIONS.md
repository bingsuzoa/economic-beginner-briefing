# Architecture Decisions

이 문서는 Foundation 작업 중 발생한 주요 설계 결정을 기록하기 위한 문서다.

Claude는 중요한 결정을 내릴 때 아래 형식으로 항목을 추가한다.

## ADR-001: TypeScript 모듈 시스템

### 상태

Proposed

### 결정

ESM 또는 CommonJS 중 선택 후 기록

### 이유

...

### 대안

...

### 영향

...

---

## ADR-002: Zod 스키마와 TypeScript 타입 관리 방식

### 상태

Proposed

### 결정

...

### 이유

...

### 대안

...

### 영향

...

---

## ADR-003: 날짜 라이브러리

### 상태

Proposed

### 결정

표준 Date, Temporal polyfill, date-fns-tz, Luxon 중 선택

### 필수 조건

- Asia/Seoul 기준 계산
- 테스트 가능
- 시스템 타임존 비의존
- 전날 시작·종료 시각의 명확성

### 이유

...

### 영향

...
