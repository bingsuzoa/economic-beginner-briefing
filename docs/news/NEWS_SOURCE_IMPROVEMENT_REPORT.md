# 뉴스 수집 소스 및 기사 선택 구조 개선 보고서

## 변경 요약

뉴스 수집 파이프라인의 소스 품질, 중복 제거, 관련성 평가, 다양성 확보를 개선했다.

## 1. RSS 소스 변경

### 수정

| 언론사 | 변경 내용 | 사유 |
|---|---|---|
| SBS Biz | `sectionId=01` → `sectionId=02` | 기존 URL이 정치 섹션을 수집하는 버그 |
| KBS World | 제거 | 해외 서비스용 RSS, 접속 불가 |
| 서울경제 | 신규 추가 | 경제 전문 언론사, RSS 정상 동작 확인 |

### 최종 소스 (5개)

1. 연합뉴스 (`yna.co.kr/rss/economy.xml`)
2. 한국경제 (`hankyung.com/feed/economy`)
3. 매일경제 (`mk.co.kr/rss/30100041/`)
4. SBS Biz (`sectionId=02`)
5. 서울경제 (`sedaily.com/RSS/Economy`)

## 2. 크로스소스 동일 사건 그룹화

기존: 동일 출처 내에서만 제목 유사도를 비교하여 중복 제거. 다른 출처의 동일 사건 보도는 모두 유지.

변경: 다른 출처에서도 제목 유사도가 높은 기사를 `NewsEventGroup`으로 묶고, 정보 품질(요약 길이, 본문 유무, 구체적 수치 포함, 낚시성 제목 여부)을 기준으로 대표 기사를 선정한다.

- 대표 기사 선정은 배열 순서에 의존하지 않음
- `EnhancedDeduplicationResult.eventGroups`로 그룹 정보 접근 가능
- 기존 `DeduplicationResult` 인터페이스와 하위 호환 유지

## 3. 개인 재테크 관련성 평가

`relevanceScorer.ts`를 신규 추가하여 키워드 기반 관련성 점수(0-5)를 평가한다.

| 점수 | 기준 | 예시 |
|---|---|---|
| 5 | 가계 재정에 직접 영향 | 기준금리 변경, 대출 규제 변경 |
| 4 | 큰 개인 재정 영향 | 주택 가격 동향, 연금 변경 |
| 3 | 중간 영향 | 물가, 환율, 공공요금 |
| 2 | 간접 영향 | 증시 동향, 산업 경기 |
| 1 | 최소 영향 | 기업 인사, M&A |
| 0 | 무관 | 경제 카테고리 미해당 |

- AI 호출 없이 제목+요약 텍스트 분석으로 수행
- `Article` 타입을 수정하지 않음 (별도 `RelevanceScore` 반환)
- `runDailyBriefing`에서 수집 후 분석 전에 적용

## 4. 카테고리 및 출처 다양성 정책

`diversitySelector.ts`를 신규 추가하여 다음 원칙을 적용한다:

- 관련성 점수가 낮은 기사(기본 2점 미만) 우선순위 하향
- 동일 언론사 최대 5개 (configurable)
- 동일 카테고리 최대 4개 (configurable)
- 품질과 관련성이 우선, 다양성은 한도 초과 시 적용

설정값: `constants.ts`의 `DIVERSITY` 상수

## 5. 파이프라인 흐름 변경

```
RSS 수집 (각 소스, 병렬)
  → 소스별 수집 통계 기록 (rawCount, durationMs)
  → 날짜 필터
  → 품질 검증
  → 크로스소스 동일 사건 그룹화 + 대표 기사 선정
  → 개인 재테크 관련성 평가 (runDailyBriefing)
  → 카테고리/출처 균형 고려 최종 선택 (runDailyBriefing)
  → AI 상세 분석
  → 발행
```

## 6. SourceCollectionReport 확장

기존 필드를 유지하면서 선택 필드를 추가했다:

```ts
rawCount?: number;          // RSS 원본 아이템 수
dateFilteredCount?: number; // 시간 필터 통과 수
qualityPassedCount?: number; // 품질 검증 통과 수
deduplicatedCount?: number; // 중복 제거 후 유지 수
durationMs?: number;        // 소스별 소요 시간
```

## 변경 파일 목록

| 파일 | 변경 유형 |
|---|---|
| `src/collectors/sources/SBSBizSourceAdapter.ts` | URL 수정 |
| `src/collectors/sources/KBSSourceAdapter.ts` | 삭제 |
| `src/collectors/sources/SedailySourceAdapter.ts` | 신규 |
| `src/collectors/RealNewsCollector.ts` | 소스 변경, durationMs 추가 |
| `src/collectors/filters/duplicateRemover.ts` | 크로스소스 그룹화 추가 |
| `src/collectors/filters/relevanceScorer.ts` | 신규 |
| `src/collectors/filters/diversitySelector.ts` | 신규 |
| `src/collectors/filters/categoryClassifier.ts` | 키워드 보강 |
| `src/config/constants.ts` | DIVERSITY 설정 추가 |
| `src/domain/article.ts` | SourceCollectionReport 선택 필드 추가 |
| `src/app/runDailyBriefing.ts` | 관련성+다양성 단계 추가 |
| `src/index.ts` | 신규 모듈 export |
| `tests/collectors/RealNewsCollector.test.ts` | 소스 변경 반영, 통계 테스트 추가 |
| `tests/collectors/filters/duplicateRemover.test.ts` | 크로스소스 그룹화 테스트 추가 |
| `tests/collectors/sources/SedailySourceAdapter.test.ts` | 신규 |
| `tests/collectors/filters/relevanceScorer.test.ts` | 신규 |
| `tests/collectors/filters/diversitySelector.test.ts` | 신규 |

## 검증 결과

```
npm run typecheck  ✅ 통과
npm run lint       ✅ 통과
npm test           ✅ 247 tests passed (32 files)
npm run build      ✅ 통과
```

## 남은 문제

- 실제 RSS 수집 테스트는 네트워크 환경에 따라 수행 필요
- `서울경제` RSS가 향후 URL 변경 또는 접속 불가 시 어댑터 수정 필요
- 관련성 점수 임계값(기본 2점)은 실제 운영 데이터를 보며 조정 가능

## 후속 브랜치 영향

- `SourceCollectionReport`에 선택 필드만 추가했으므로 기존 코드에 영향 없음
- `DeduplicationResult` 인터페이스를 확장(`EnhancedDeduplicationResult`)하여 하위 호환 유지
- `runDailyBriefing`의 파이프라인 변경이 있으나, `ApplicationDeps` 인터페이스 변경은 없음
