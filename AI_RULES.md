# AI_[RULES.md](http://RULES.md)

# AI Development Rules

이 문서는 프로젝트에 참여하는 모든 AI가 반드시 따라야 하는 개발 규칙이다.

---

# 1. 목표

가장 중요한 목표는

코드를 많이 작성하는 것이 아니다.

유지보수하기 쉬운 구조를 만드는 것이다.

항상

가독성

↓

테스트 가능성

↓

확장성

↓

구현 속도

순으로 우선순위를 둔다.

---

# 2. 구현 원칙

필요한 기능만 구현한다.

다음 행동을 금지한다.

- 미리 구현하기

- 사용하지 않는 코드 작성

- 추측해서 기능 추가

- TODO만 남기고 끝내기

---

# 3. 작업 범위

현재 브랜치의 역할만 구현한다.

다른 브랜치의 책임을 구현하지 않는다.

예를 들어

feature/news-collector

에서는

- AI 분석

- 이메일

- Notion

기능을 구현하지 않는다.

---

# 4. 공통 타입

DATA_[CONTRACTS.md](http://CONTRACTS.md)가 기준이다.

공통 타입은 변경하지 않는다.

변경이 필요하면

CHANGE_[REQUEST.md](http://REQUEST.md)

를 작성한다.

---

# 5. 외부 API

외부 API는 직접 호출하지 않는다.

항상 Interface를 통해 의존한다.

예)

NewsAnalyzer

↓

OpenAIAnalyzer

MockAnalyzer

---

# 6. 의존성 방향

항상

App

↓

Domain

↓

Interface

↓

Implementation

순서를 유지한다.

Implementation끼리 직접 의존하지 않는다.

---

# 7. Domain 규칙

Domain은

외부 SDK를 몰라야 한다.

금지

- Axios Response

- Notion SDK

- OpenAI SDK

- Express Request

Domain에는

순수한 데이터만 존재한다.

---

# 8. 함수 작성 규칙

한 함수는

한 가지 책임만 가진다.

20~30줄 이상이면

분리를 고려한다.

중첩은 2단계 이하를 권장한다.

---

# 9. 테스트

새로운 기능은

반드시 테스트를 작성한다.

버그를 수정하면

재현 테스트를 먼저 작성한다.

---

# 10. 에러 처리

catch에서

빈 처리

금지.

에러를 무시하지 않는다.

retry 가능한지

판단하여 반환한다.

---

# 11. 로그

로그에는

Secret

API Key

OAuth

Password

Cookie

를 출력하지 않는다.

---

# 12. AI가 모르는 경우

추측하지 않는다.

다음처럼 말한다.

"현재 문서만으로는 결정할 수 없습니다."

필요한 정보를 요청한다.

---

# 13. 코드 스타일

다음 순서를 따른다.

읽기 쉬움

↓

테스트 가능

↓

성능

↓

짧은 코드

짧다고 좋은 코드가 아니다.

---

# 14. 완료 기준

작업 완료 전

반드시

- typecheck

- lint

- test

- build

를 수행한다.

실패하면

완료로 간주하지 않는다.

---

# 15. 보고 형식

항상

## 구현 내용

## 변경 파일

## 테스트 결과

## 남은 문제

## 영향 범위

형식으로 보고한다.

---

# 16. 금지사항

절대

- 자동 병합

- 강제 Push

- Force Reset

- 공통 타입 임의 수정

- 문서와 다른 구현

을 하지 않는다.

---

# 17. 설계 우선순위

항상 다음 순서대로 판단한다.

1. PRODUCT_[REQUIREMENTS.md](http://REQUIREMENTS.md)

2. [ARCHITECTURE.md](http://ARCHITECTURE.md)

3. DATA_[CONTRACTS.md](http://CONTRACTS.md)

4. 현재 TASK 문서

5. 구현 편의성

구현이 어렵다는 이유로

설계를 변경하지 않는다.

---

# 18. 가장 중요한 원칙

좋은 AI는

많이 구현하는 AI가 아니다.

프로젝트 전체 구조를

망치지 않는 AI다.