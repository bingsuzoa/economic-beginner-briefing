# Economic Beginner Briefing

경제를 전혀 모르는 사용자를 위해, 전날의 경제·재테크·부동산 뉴스를 수집하고 중요한 뉴스를 선별한 뒤 쉬운 말로 설명하여 Notion과 이메일로 전달하는 자동화 프로젝트입니다.

## 프로젝트 목표

이 프로젝트는 단순한 뉴스 요약기가 아닙니다.

사용자가 뉴스를 읽고 다음 질문에 답을 얻도록 만드는 것이 목표입니다.

- 무슨 일이 발생했는가?
- 기존에는 어떤 상황이었는가?
- 무엇이 달라졌는가?
- 왜 이런 변화가 생겼는가?
- 누가 유리하고 누가 불리한가?
- 일반 가정과 신혼부부에게 어떤 영향이 있는가?
- 앞으로 어떤 일이 발생할 가능성이 있는가?
- 지금 확인하거나 행동할 것이 있는가?
- 기사에 나온 경제용어는 무슨 뜻인가?

## 주요 처리 흐름

```text
전날 뉴스 수집
→ 날짜 및 품질 검증
→ 중복 기사 제거
→ 중요 뉴스 선별
→ 동일 사건 기사 그룹화
→ 대표 기사 선정
→ 경제 초보자용 해설 생성
→ Notion 저장
→ 이메일 발송
→ 실행 결과 기록
```

## 기술 방향

- Runtime: Node.js
- Language: TypeScript
- Timezone: Asia/Seoul
- Test framework: Vitest
- Validation: Zod
- AI analysis: OpenAI API
- Storage/Delivery: Notion API, Email API
- Scheduler: GitHub Actions
- Package manager: npm

## Foundation 브랜치의 범위

`feature/project-foundation` 브랜치에서는 실제 외부 API 연동을 구현하지 않습니다.

이 브랜치의 목적은 다음과 같습니다.

1. 프로젝트 기본 구조 생성
2. 공통 타입 정의
3. 모듈 간 인터페이스 정의
4. 설정 및 환경변수 구조 정의
5. 테스트용 Mock 구현
6. 이후 브랜치가 따라야 할 작업 규칙 문서화
7. 최소 실행 가능한 샘플 파이프라인 작성

## Foundation 브랜치에서 구현하지 않는 것

- 실제 뉴스 RSS 수집
- 웹 크롤링
- OpenAI API 호출
- Notion API 호출
- 이메일 발송
- GitHub Actions 예약 실행
- PostgreSQL 또는 외부 데이터베이스 연동

## 문서 읽는 순서

Claude는 작업 전에 다음 문서를 순서대로 읽어야 합니다.

1. `docs/PRODUCT_REQUIREMENTS.md`
2. `docs/ARCHITECTURE.md`
3. `docs/DATA_CONTRACTS.md`
4. `docs/AI_EXPLANATION_POLICY.md`
5. `docs/ERROR_HANDLING.md`
6. `docs/BRANCH_TASKS.md`
7. `docs/FOUNDATION_TASK.md`

## 기본 명령어

```bash
npm install
npm run typecheck
npm test
npm run lint
npm run build
```

## 완료 조건

Foundation 브랜치는 다음 조건을 모두 만족해야 완료된 것으로 봅니다.

- TypeScript strict mode가 활성화되어 있다.
- 공통 타입과 인터페이스가 정의되어 있다.
- Mock 구현으로 전체 파이프라인이 실행된다.
- 단위 테스트가 통과한다.
- 외부 API 키가 없어도 테스트와 빌드가 가능하다.
- 각 모듈의 책임과 입력·출력 규격이 문서와 일치한다.
- 실제 비밀값이 저장소에 포함되지 않는다.
