# Economic Beginner Briefing (Thoth)

연합뉴스 전체 스트림에서 매일의 경제 관측을 골라 세계의 상황, 주체의 이해관계, 국가 간 관계와 시장 전달 경로를 초보자에게 설명하는 Spring Boot + React 서비스입니다.

운영 구조와 개선 원칙은 [docs/ECONOMIC_FLOW_OPERATIONS.md](docs/ECONOMIC_FLOW_OPERATIONS.md)를 먼저 읽으세요. 상세 설계는 [docs/ECONOMIC_FLOW_DAILY_BRIEFING_FINAL_DESIGN_V1.md](docs/ECONOMIC_FLOW_DAILY_BRIEFING_FINAL_DESIGN_V1.md), 프롬프트 계약은 [docs/ECONOMIC_FLOW_LLM_PROMPT_DESIGN_V1.md](docs/ECONOMIC_FLOW_LLM_PROMPT_DESIGN_V1.md)에 있습니다.

## 현재 구조

```text
매시 05분 연합뉴스 RSS 수집
→ 매일 05:10 직전 24시간 제목 사전선별(Luna)
→ 제목·요약 정밀선별(Luna)
→ 선택 기사 원문 관측+근거 문단 추출(Luna)
→ 과거 관측·경제원리 검색(PostgreSQL/pgvector)
→ 흐름 묶음과 인과 설계(Terra)
→ 초보자용 설명 편집(Luna)
→ 코드 검증 후 날짜별 revision 저장·공개
```

경제흐름 핵심 테이블은 `articles`, `article_observations`, `economic_principle_chunk`, `daily_briefings` 네 개입니다. 인증과 환율 테이블은 별도 기능으로 유지합니다.

## 요구사항

- Java 21
- PostgreSQL 14+와 pgvector
- Node.js/npm
- OpenAI API key

## 필수 환경변수

```dotenv
OPENAI_API_KEY=
ADMIN_TOKEN=
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=
AUTH_EMAIL_ENCRYPTION_KEY=
AUTH_EMAIL_HASH_KEY=
```

주요 선택 설정:

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/economic_briefing?stringtype=unspecified
SCHEDULER_ENABLED=true
COLLECT_CRON=0 5 * * * *
DAILY_CRON=0 10 5 * * *
OPENAI_SCREENING_MODEL=gpt-5.6-luna
OPENAI_EXTRACTION_MODEL=gpt-5.6-luna
OPENAI_SYNTHESIS_MODEL=gpt-5.6-terra
OPENAI_WRITING_MODEL=gpt-5.6-luna
OPENAI_EMBEDDING_MODEL=text-embedding-3-large
DAILY_COST_USD=0.14
```

## 실행과 테스트

```bash
./gradlew clean test
cd frontend
npm install
npm run build
node ../scripts/check-mock-data.mjs
cd ..
./gradlew bootRun
```

Flyway는 애플리케이션 시작 때 자동 실행됩니다. 적용된 migration 파일을 수정하지 말고 새 번호를 추가하세요.

## API

공개:

```text
GET /api/briefings/latest
GET /api/briefings/{yyyy-MM-dd}
GET /api/health/briefing
GET /api/exchange-rate/**
POST /api/auth/**
```

관리자 Bearer token 필요:

```text
GET  /api/admin/briefings/runs
GET  /api/admin/briefings/runs/{id}
POST /api/admin/briefings/{yyyy-MM-dd}/run
```

수동 실행은 날짜 전체를 새 revision으로 비동기 실행합니다. 실패 revision은 공개하지 않고 이전 SUCCESS를 유지합니다.

## 배포

`main` push는 GitHub Actions와 Windows self-hosted runner를 통해 **DEV :8081 / economic_briefing_dev만** 자동 배포합니다. PROD :3000은 자동 workflow 범위가 아닙니다.

운영 배포는 Windows 관리자 PowerShell에서 실행합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\deploy.ps1 -Test
```

스크립트는 JAR과 `frontend/dist`를 백업하고 테스트·빌드·재시작·health check를 수행합니다. V24 같은 DB 파괴적 migration 전에는 애플리케이션 백업과 별개로 PostgreSQL dump를 만드세요.

## 품질 기준

- 전망·주장·계획을 사실로 바꾸지 않는다.
- 원문 근거 문단 없는 관측은 사용하지 않는다.
- 같은 상류 충격이나 최종 수요를 기사별 흐름으로 쪼개지 않는다.
- 직접 근거 없는 인과는 가능성·압력·조건으로 표현한다.
- 과거 생성 설명이 아니라 과거 원문 관측만 검색한다.
- 흐름 수를 고정하지 않는다.
- 검증 LLM과 메타 JSON 필드를 늘리지 않는다.
- 일일 usage와 비용을 `daily_briefings`에서 확인한다.
