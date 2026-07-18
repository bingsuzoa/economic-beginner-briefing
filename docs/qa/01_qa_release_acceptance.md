# RELEASE_ACCEPTANCE_TEST.md

# Economic Beginner Briefing
# Release Acceptance Test Plan

Version: 1.0

Status: Ready

Document Owner: QA Team

---

# 1. 목적

본 문서는 Economic Beginner Briefing 프로젝트를
Production 환경에 배포하기 전에 수행해야 하는
최종 Release Acceptance Test 절차를 정의한다.

본 테스트는 단순 기능 확인이 아니다.

다음 사항을 검증한다.

- 기능이 정상 동작하는가
- 장애 발생 시 서비스가 안전하게 복구되는가
- 운영자가 서비스 상태를 확인할 수 있는가
- 운영 로그가 충분히 남는가
- GitHub Actions에서 정상 운영 가능한가
- Production 환경에서 안정적으로 서비스 가능한가

본 테스트를 모두 통과해야 Production Ready 상태로 판단한다.

---

# 2. Claude 역할

이번 작업에서 Claude는 개발자가 아니다.

Claude는 QA Lead 역할을 수행한다.

즉,

기능을 추가하는 것이 아니라

서비스를 부수려고 시도하는 사람이다.

테스트 중 발견된 문제는

절대로 무시하지 않는다.

모든 문제는

원인 분석

↓

수정

↓

재검증

↓

PASS 확인

순으로 진행한다.

---

# 3. 테스트 원칙

절대로

"아마 될 것이다"

라고 판단하지 않는다.

반드시

실행

↓

결과 확인

↓

PASS / FAIL

을 기록한다.

---

모든 테스트는

재현 가능해야 한다.

즉

다른 사람이 동일한 절차를 수행하면

같은 결과가 나와야 한다.

---

모든 FAIL은

원인을 기록한다.

예시

FAIL

원인

OpenAI API Timeout

조치

Retry Logic 추가

재검증

PASS

---

# 4. 테스트 범위

이번 QA는 아래 범위를 포함한다.

## Pipeline

- News Collector
- Duplicate Filter
- AI Analysis
- Notion Publisher

---

## Database

- Pipeline Run 저장
- Pipeline Log 저장
- Pipeline Item 저장

---

## Dashboard

- Status
- History
- Detail
- News
- Log

---

## API

Admin API 전체

---

## Scheduler

Scheduler

또는

GitHub Actions

---

## Security

Admin 접근

민감정보

로그

---

## Performance

대량 뉴스

실행시간

메모리

---

## Regression

기존 기능이 깨지지 않았는지 확인

---

# 5. 테스트 제외 범위

이번 QA에서는 아래는 제외한다.

- 디자인 개선
- UI 애니메이션
- Email Publisher
- 모바일 UX 개선
- SEO
- 접근성 고도화

단

기능 오류는 포함한다.

---

# 6. 테스트 환경

Production과 최대한 동일한 환경에서 수행한다.

확인 항목

- Node Version
- Package Version
- Database Version
- Environment Variables
- OpenAI
- Notion
- News API

---

# 7. 테스트 데이터

테스트는

Mock 데이터만으로 끝내지 않는다.

실제 뉴스

↓

실제 AI

↓

실제 Notion

까지 확인한다.

단

비용이 큰 테스트는 최소 횟수로 수행한다.

---

# 8. PASS 기준

테스트 하나는 아래를 만족해야 PASS이다.

✔ 기대 결과와 동일

✔ 예외 없음

✔ DB 정상 저장

✔ Dashboard 정상 표시

✔ API 정상 응답

✔ 로그 정상 저장

---

FAIL 기준

아래 하나라도 만족하면 FAIL이다.

- 오류 발생

- DB 저장 실패

- Dashboard 미표시

- API 오류

- 로그 누락

- 상태 불일치

---

# 9. Bug Severity

## Critical

서비스 운영 불가

예

Pipeline 실행 불가

DB 저장 실패

GitHub Actions 실패

데이터 유실

Production 장애

Release 불가

---

## Major

핵심 기능 오류

예

AI 실패

Notion 실패

Dashboard 오류

Admin API 오류

수정 후 재검증

---

## Minor

서비스는 가능

예

UI 오류

문구 오류

색상

정렬

오타

Release 가능

---

# 10. Release Gate

아래 조건을 모두 만족해야 Release 가능하다.

Critical

0건

Major

0건

PASS Rate

95% 이상

Regression

100%

Pipeline

정상

Dashboard

정상

DB

정상

GitHub Actions

정상

---

# 11. 테스트 수행 규칙

순서를 변경하지 않는다.

1.

환경 확인

↓

2.

Pipeline 테스트

↓

3.

DB 확인

↓

4.

Dashboard 확인

↓

5.

API 확인

↓

6.

예외 테스트

↓

7.

보안 테스트

↓

8.

성능 테스트

↓

9.

회귀 테스트

↓

10.

최종 Release 판단

---

# 12. 테스트 중 금지사항

절대로

테스트를 통과시키기 위해

결과를 수정하지 않는다.

테스트 중

코드를 수정했다면

반드시

처음부터 다시 검증한다.

---

테스트를 건너뛰지 않는다.

모든 Test Case를 수행한다.

---

# 13. 테스트 증적

각 테스트는 아래 내용을 남긴다.

Test Case

PASS / FAIL

실행시간

실행환경

원인

수정내용

재검증 결과

---

# 14. 최종 승인 기준

Claude는 마지막에 아래 둘 중 하나만 선택한다.

✅ Production Ready

또는

❌ Release Hold

애매한 판단은 하지 않는다.

---

# 15. Claude 최종 역할

모든 Test Case 수행 후

아래 내용을 반드시 작성한다.

- PASS 개수
- FAIL 개수
- Critical Bug
- Major Bug
- Minor Bug
- 수정한 내용
- 남은 위험요소
- Release 가능 여부

이 문서는 Production 배포 승인 문서이다.

따라서 테스트를 수행하는 동안에는
개발자가 아니라 QA Lead의 관점에서 판단한다.