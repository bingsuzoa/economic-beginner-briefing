# 뉴스 수집 균형 개선 보고서

## 변경 요약

### 1. 신규 RSS 소스 추가

검증된 2개 소스를 추가하여 총 7개 소스에서 수집합니다.

| 소스 | RSS URL | 상태 |
|------|---------|------|
| 뉴시스 | `https://www.newsis.com/RSS/economy.xml` | 신규 추가 |
| 머니투데이 | `https://rss.mt.co.kr/mt_news.xml` | 신규 추가 |

**검증 결과 미추가 소스:**
- 이데일리: SSL 오류/406 응답 - RSS 서비스 불안정
- 조선비즈: 404 응답 - RSS 서비스 종료 추정
- 기타 대안(아시아경제, 파이낸셜뉴스, 헤럴드경제 등): 404/파싱 오류

### 2. 카테고리 분류 키워드 보강

`categoryClassifier.ts`에 추가된 키워드:

- **deposit_saving**: 자유적금, 정기저축, 저축은행
- **loan**: 마이너스통장, 스트레스DSR
- **housing**: 분양가, 입주물량, 주택공급, 신축, 준공
- **jeonse_monthly_rent**: 임대차보호법, 계약갱신
- **tax**: 근로장려금, EITC
- **pension**: 노후자금, 노후대비
- **insurance**: 보장보험, 연금보험
- **cost_of_living**: 통신비, 구독료, 카드혜택, 할인
- **investment**: 주주총회, 배당금, 상장폐지, 자산배분, 재테크, 금융상품, 신탁, MMF, RP, ELS
- **government_support**: 청년도약, 주거급여, 아동수당, 육아휴직
- **employment_income**: 구직, 채용
- **household_debt**: 채무조정
- **interest_rate**: 기준금리 동결

### 3. 관련성 점수 키워드 보강

`relevanceScorer.ts`에 추가된 키워드:

| 점수 | 추가 키워드 |
|------|-----------|
| 5점 | 스트레스DSR, 전세보증금 관리 |
| 4점 | 분양가 상한제, 입주물량, 주택공급, IRP, 연금저축 |
| 3점 | 자산배분, 재테크, 금융상품, 통신비, 구독료, 카드혜택, 주주총회, 배당금, 공모주, CMA, MMF |

### 4. 선택 정책 변경

| 항목 | 변경 전 | 변경 후 |
|------|---------|---------|
| MAX_ARTICLES_PER_SOURCE | 5 | 3 |
| MAX_ARTICLES_PER_CATEGORY | 4 | 3 |
| MIN_PERSONAL_FINANCE_RELEVANCE | 2 | 3 |
| SOFT_MAX_OVERRIDE_SCORE | (없음) | 5 |

**softMax 개념 도입**: 관련성 5점(즉시 확인 필요) 기사는 카테고리 제한을 1건 초과 허용.

### 5. AI 프롬프트 개선

- 개인 재테크 관련성 낮은 기사 제외 지시 추가
- 같은 사건 중복 그룹화 강화
- 카테고리 다양성 고려 지시 추가
- 주/보조 카테고리 구분 안내
- 근거 없는 영향 생성 금지 강화

### 6. 실행 로그 개선

`COLLECT_FILTERING_STATS` 코드로 필터링 통계를 로그에 기록:
- 검증 통과 수, 관련성 통과 수, 관련성 제외 수
- 다양성 선택 수, 소스 제한 제외 수, 카테고리 제한 제외 수
- 출처별/카테고리별 분포

## 변경 파일

### 신규 파일
- `src/collectors/sources/NewsisSourceAdapter.ts`
- `src/collectors/sources/MoneyTodaySourceAdapter.ts`
- `tests/collectors/sources/NewsisSourceAdapter.test.ts`
- `tests/collectors/sources/MoneyTodaySourceAdapter.test.ts`
- `scripts/measure-coverage.ts`

### 수정 파일
- `src/collectors/RealNewsCollector.ts` (소스 등록)
- `src/collectors/filters/categoryClassifier.ts` (키워드 보강)
- `src/collectors/filters/relevanceScorer.ts` (키워드 보강)
- `src/collectors/filters/diversitySelector.ts` (softMax 도입)
- `src/config/constants.ts` (정책 상수 변경)
- `src/analyzers/openai/prompts/systemPrompt.ts` (프롬프트 개선)
- `src/analyzers/openai/prompts/buildAnalysisPrompt.ts` (프롬프트 개선)
- `src/app/runDailyBriefing.ts` (필터링 통계 로그)
- `tests/collectors/RealNewsCollector.test.ts` (어댑터 수 업데이트)
- `tests/collectors/filters/categoryClassifier.test.ts` (신규 키워드 테스트)
- `tests/collectors/filters/relevanceScorer.test.ts` (신규 키워드 테스트)
- `tests/collectors/filters/diversitySelector.test.ts` (softMax 테스트)
- `tests/app/runDailyBriefing.test.ts` (필터링 통계 반영)
- `tests/app/integration.test.ts` (필터링 반영)

## 테스트 결과

```
Test Files  34 passed (34)
     Tests  261 passed (261)
```

## 후속 브랜치에 미치는 영향

- `NewsCategory` 타입 변경 없음 (기존 16개 유지)
- `Article`, `ExecutionLog` 등 공통 타입 변경 없음
- Scheduler, AI 분석, Notion, Email 기능 영향 없음
- 수집 기사 수와 최종 선택 기사의 카테고리 분포가 달라질 수 있으나, 모든 하위 타입은 동일
