# [CLAUDE.md](http://CLAUDE.md)

## 프로젝트 목적

경제 초보자가 전날의 경제·재테크·부동산 뉴스를 이해할 수 있도록 수집, 분석, 발행하는 자동화 프로젝트입니다.

단순 요약보다 다음 내용을 설명하는 것을 중요하게 봅니다.

- 기존에는 어땠는지
- 무엇이 바뀌었는지
- 왜 바뀌었는지
- 일반 가정에 어떤 영향이 있는지
- 예상과 확정 사실이 어떻게 다른지

## 작업 시작 전

다음 문서를 읽습니다.

1. [`README.md`](http://README.md)
2. `docs/PRODUCT_[REQUIREMENTS.md](http://REQUIREMENTS.md)`
3. `docs/[ARCHITECTURE.md](http://ARCHITECTURE.md)`
4. `docs/DATA_[CONTRACTS.md](http://CONTRACTS.md)`
5. `docs/BRANCH_[TASKS.md](http://TASKS.md)`
6. 현재 브랜치에 해당하는 TASK 문서

브랜치별 TASK 문서 예시:

- `feature/project-foundation` → `docs/FOUNDATION_[TASK.md](http://TASK.md)`
- `feature/news-collector` → `docs/NEWS_COLLECTOR_[TASK.md](http://TASK.md)`
- `feature/ai-analysis` → `docs/AI_ANALYSIS_[TASK.md](http://TASK.md)`
- `feature/notion-publisher` → `docs/NOTION_PUBLISHER_[TASK.md](http://TASK.md)`
- `feature/email-publisher` → `docs/EMAIL_PUBLISHER_[TASK.md](http://TASK.md)`

## 작업 규칙

- 현재 브랜치의 담당 범위만 구현합니다.
- `docs/DATA_[CONTRACTS.md](http://CONTRACTS.md)`의 공통 타입을 임의로 변경하지 않습니다.
- 공통 타입 변경이 필요하면 `CHANGE_[REQUEST.md](http://REQUEST.md)`에 제안합니다.
- 도메인 타입에서 외부 SDK 타입을 사용하지 않습니다.
- 실제 API 키와 비밀값을 코드, 문서, 로그에 남기지 않습니다.
- TASK 문서에 없는 기능을 미리 구현하지 않습니다.
- `main` 또는 `develop`에 자동 병합하지 않습니다.

## 완료 조건

작업 후 다음 명령을 실행합니다.

```bash
npm run typecheck
npm run lint
npm test
npm run build

```

완료 보고에는 다음을 포함합니다.

- 구현 요약
- 변경 파일
- 테스트 결과
- 남은 문제
- 후속 브랜치에 미치는 영향

