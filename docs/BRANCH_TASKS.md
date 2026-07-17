# Branch Tasks

## 1. 브랜치 전략

```text
main
└─ develop
   ├─ feature/project-foundation
   ├─ feature/news-collector
   ├─ feature/ai-analysis
   ├─ feature/notion-publisher
   ├─ feature/email-publisher
   ├─ feature/integration
   └─ feature/scheduler
```

## 2. 공통 규칙

- 모든 feature 브랜치는 최신 `develop`에서 생성한다.
- 한 브랜치는 자신의 담당 영역만 수정한다.
- 공통 타입을 임의로 변경하지 않는다.
- 공통 타입 변경이 필요하면 `CHANGE_REQUEST.md`를 작성한다.
- 작업 완료 전 테스트와 typecheck를 실행한다.
- 실제 API 키를 커밋하지 않는다.
- 자동 병합하지 않는다.
- 완료 후 변경 파일, 테스트 결과, 남은 문제를 보고한다.

## 3. feature/project-foundation

담당: Claude

구현:

- 프로젝트 기본 구조
- TypeScript strict 설정
- 공통 타입
- Zod 스키마
- Collect, Analyze, Publish 인터페이스
- Mock 구현체
- Mock 전체 파이프라인
- 기본 테스트
- 환경변수 검증 기본 구조
- 에러 타입
- 문서 정리

구현 금지:

- 실제 RSS
- OpenAI API
- Notion API
- Email API
- 스케줄러

## 4. feature/news-collector

담당: Claude

담당 경로:

```text
src/collectors/**
tests/collectors/**
```

구현:

- RSS 및 공식기관 자료 수집
- Asia/Seoul 전날 날짜 필터
- 기사 정규화
- 중복 제거
- 수집처별 오류 격리
- `CollectNewsResult` 반환

## 5. feature/ai-analysis

담당: Claude

담당 경로:

```text
src/analyzers/**
tests/analyzers/**
```

구현:

- OpenAI API 연동
- 중요 기사 선별
- 동일 사건 그룹화
- 대표 출처 선정
- 경제 초보자용 해설
- JSON Schema 또는 Zod 검증
- 사실·검토·예상 구분
- `AnalyzeNewsResult` 반환

## 6. feature/notion-publisher

담당: Codex

담당 경로:

```text
src/publishers/notion/**
tests/publishers/notion/**
```

구현:

- Notion 데이터베이스 저장
- 기사별 속성 매핑
- 중복 저장 방지
- DRY_RUN
- 단위 테스트

## 7. feature/email-publisher

담당: Codex

담당 경로:

```text
src/publishers/email/**
tests/publishers/email/**
```

구현:

- 모바일 친화 HTML 템플릿
- 이메일 전송 어댑터
- HTML escape
- DRY_RUN
- 단위 테스트

## 8. feature/integration

담당: Claude

구현:

- 실제 Collector, Analyzer, Publishers 연결
- 실행 상태 관리
- 부분 실패 처리
- 중복 실행 정책
- 통합 테스트
- 비용 및 토큰 사용량 점검

## 9. feature/scheduler

담당: Codex 초안, Claude 최종 검토

구현:

- GitHub Actions
- 한국시간 기준 실행 시간
- 수동 실행 지원
- Secrets 참조
- 실패 로그 확인 방법 문서화

## 10. CHANGE_REQUEST.md 형식

```md
# Change Request

## 요청 브랜치

feature/...

## 변경 대상

AnalyzedNews.expectedNextEffects

## 현재 문제

...

## 제안 변경

...

## 다른 브랜치 영향

...

## 하위 호환 방법

...
```
