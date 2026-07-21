# Spring Boot 마이그레이션 설계 문서

## 목차

1. [현재 Node.js 프로젝트 분석](#1-현재-nodejs-프로젝트-분석)
2. [Java 프로젝트 구조 제안](#2-java-프로젝트-구조-제안)
3. [Node.js → Java 파일 매핑](#3-nodejs--java-파일-매핑)
4. [Spring Boot 아키텍처](#4-spring-boot-아키텍처)
5. [Java 클래스 전체 설계](#5-java-클래스-전체-설계)
6. [마이그레이션 계획](#6-마이그레이션-계획)

---

## 1. 현재 Node.js 프로젝트 분석

### 1.1 기능 목록

| 기능 | 설명 | 구현 상태 |
|------|------|-----------|
| 뉴스 수집 | 10개 RSS 소스에서 경제 뉴스 수집 | 완료 |
| 다단계 필터링 | 날짜/품질/중복/카테고리/관련성/다양성 필터 | 완료 |
| AI 분석 | OpenAI GPT-4o로 경제 뉴스 분석/요약 | 완료 |
| 브리핑 생성 | 분석 결과를 Briefing 객체로 구성 | 완료 |
| Notion 발행 | Notion API로 브리핑 페이지 생성 | 완료 |
| 이메일 발행 | 이메일로 브리핑 발송 | 미구현 (인터페이스만) |
| 실행 추적 | 중복 실행 방지, 실행 로그 기록 | 완료 |
| 파이프라인 잠금 | DB 기반 동시 실행 방지 | 완료 |
| 관리자 대시보드 | Express + PostgreSQL 기반 모니터링 | 완료 |
| 스케줄러 | GitHub Actions 매시간 자동 실행 | 완료 |
| Watchdog | 실행 감시 및 재시도 워크플로우 | 완료 |
| Mock 구현체 | API 키 없이 테스트 가능한 Mock 클래스 | 완료 |

### 1.2 파일 역할 분석

#### 핵심 오케스트레이션
| 파일 | 역할 |
|------|------|
| `src/app/runDailyBriefing.ts` | 메인 파이프라인 (수집→분석→발행) 오케스트레이션 |
| `src/app/createApplication.ts` | DI 컨테이너 (의존성 조합) |
| `src/app/createDefaultApplication.ts` | 환경에 따른 Mock/Real 구현체 선택 |
| `src/app/ExecutionTracker.ts` | 중복 실행 감지 |
| `src/app/validatePipelineData.ts` | 파이프라인 단계별 데이터 검증 |
| `src/cli/runDailyBriefingCli.ts` | CLI 진입점 |
| `src/index.ts` | 메인 엔트리포인트 |

#### 도메인 타입
| 파일 | 역할 |
|------|------|
| `src/domain/article.ts` | Article, NewsCategory, 수집 요청/결과 타입 |
| `src/domain/analyzedNews.ts` | AnalyzedNews, EconomicTerm, 분석 요청/결과 타입 |
| `src/domain/briefing.ts` | Briefing, 발행 요청/결과 타입 |
| `src/domain/execution.ts` | ExecutionLog, ExecutionError 타입 |

#### 수집기 (Collectors)
| 파일 | 역할 |
|------|------|
| `src/collectors/NewsCollector.ts` | 수집기 인터페이스 |
| `src/collectors/RealNewsCollector.ts` | 실제 RSS 수집 구현체 |
| `src/collectors/sources/SourceAdapter.ts` | 소스 어댑터 인터페이스 |
| `src/collectors/sources/BaseRSSAdapter.ts` | RSS 어댑터 기본 클래스 |
| `src/collectors/sources/*.ts` (10개) | 개별 뉴스 소스 어댑터 |
| `src/collectors/filters/*.ts` (6개) | 다단계 필터 체인 |
| `src/collectors/parsers/*.ts` (2개) | RSS 파싱/정규화 |

#### 분석기 (Analyzers)
| 파일 | 역할 |
|------|------|
| `src/analyzers/NewsAnalyzer.ts` | 분석기 인터페이스 |
| `src/analyzers/openai/OpenAIClient.ts` | OpenAI API 래퍼 |
| `src/analyzers/openai/OpenAINewsAnalyzer.ts` | OpenAI 분석기 구현체 |
| `src/analyzers/openai/prompts/*.ts` (3개) | 시스템/유저 프롬프트, 응답 스키마 |
| `src/analyzers/openai/utils/*.ts` (2개) | 응답 변환, 재시도 로직 |

#### 발행기 (Publishers)
| 파일 | 역할 |
|------|------|
| `src/publishers/BriefingPublisher.ts` | 발행기 인터페이스 |
| `src/publishers/notion/NotionBriefingPublisher.ts` | Notion 발행 구현체 |
| `src/publishers/notion/NotionClientAdapter.ts` | Notion API 래퍼 |
| `src/publishers/notion/buildNotionPage.ts` | Notion 블록 빌더 |

#### 인프라
| 파일 | 역할 |
|------|------|
| `src/config/env.ts` | 환경변수 로딩 + Zod 검증 |
| `src/config/constants.ts` | 상수 정의 |
| `src/errors/AppError.ts` | 커스텀 에러 클래스 |
| `src/errors/errorCodes.ts` | 에러 코드 정의 |
| `src/utils/date.ts` | KST 날짜/시간 유틸 |
| `src/utils/result.ts` | Result<T> 타입 |

#### 파이프라인 & DB
| 파일 | 역할 |
|------|------|
| `src/pipeline/PipelineLock.ts` | 파이프라인 잠금 인터페이스 |
| `src/pipeline/DbPipelineLock.ts` | DB 기반 잠금 구현체 |
| `src/pipeline/PipelineRecorder.ts` | 파이프라인 기록 인터페이스 |
| `src/pipeline/runPipeline.ts` | 파이프라인 실행 오케스트레이션 |
| `src/db/pool.ts` | DB 커넥션 풀 |
| `src/db/migrate.ts` | 스키마 생성 |
| `src/db/repositories/*.ts` (3개) | PipelineRun/Log/Item 리포지토리 |

#### 관리자 대시보드
| 파일 | 역할 |
|------|------|
| `src/admin/server.ts` | Express 앱 설정 |
| `src/admin/middleware/auth.ts` | 토큰 인증 |
| `src/admin/middleware/errorHandler.ts` | 에러 핸들링 미들웨어 |
| `src/admin/routes/*.ts` (3개) | Status/Run/Item API 라우트 |

---

## 2. Java 프로젝트 구조 제안

### 2.1 프로젝트 기본 정보

```
Group ID:    com.economicbriefing
Artifact ID: economic-briefing
Java:        21
Spring Boot: 3.4.x
Build:       Gradle (Kotlin DSL)
```

### 2.2 패키지 구조

```
src/main/java/com/economicbriefing/
├── EconomicBriefingApplication.java          # @SpringBootApplication 메인
│
├── domain/                                    # 도메인 모델 (순수 Java, 외부 의존성 없음)
│   ├── article/
│   │   ├── Article.java                       # 기사 도메인 객체
│   │   ├── NewsCategory.java                  # enum
│   │   ├── ArticleSourceType.java             # enum
│   │   └── SourceCollectionReport.java        # 소스별 수집 보고서
│   ├── analysis/
│   │   ├── AnalyzedNews.java                  # 분석된 뉴스
│   │   ├── EconomicTerm.java                  # 경제 용어
│   │   ├── NewsEvidenceStatus.java            # enum (confirmed/proposed/expected)
│   │   ├── SourceReference.java               # 출처 참조
│   │   ├── TargetAudience.java                # 대상 독자
│   │   └── ImpactAssessment.java              # 영향 평가
│   ├── briefing/
│   │   ├── Briefing.java                      # 브리핑 도메인 객체
│   │   └── BriefingMetadata.java              # 브리핑 메타데이터
│   └── execution/
│       ├── ExecutionLog.java                  # 실행 로그
│       ├── ExecutionError.java                # 실행 에러
│       ├── ExecutionStatus.java               # enum (running/success/partial_success/failed)
│       └── PublicationDecision.java           # enum (publish/skip/retry)
│
├── collector/                                 # 뉴스 수집 계층
│   ├── NewsCollector.java                     # 인터페이스
│   ├── DefaultNewsCollector.java              # 메인 구현체 (@Service)
│   ├── source/                                # RSS 소스 어댑터
│   │   ├── SourceAdapter.java                 # 인터페이스
│   │   ├── AbstractRssSourceAdapter.java      # 추상 기본 클래스
│   │   ├── YonhapSourceAdapter.java           # 연합뉴스
│   │   ├── HankyungSourceAdapter.java         # 한국경제
│   │   ├── MKSourceAdapter.java               # 매일경제
│   │   ├── SBSBizSourceAdapter.java           # SBS뉴스
│   │   ├── SedailySourceAdapter.java          # 서울경제
│   │   ├── NewsisSourceAdapter.java           # 뉴시스
│   │   ├── MoneyTodaySourceAdapter.java       # 머니투데이
│   │   ├── SegyeSourceAdapter.java            # 세계일보
│   │   ├── KhanSourceAdapter.java             # 아시아경제
│   │   └── DongaSourceAdapter.java            # 동아일보
│   ├── filter/                                # 필터 체인
│   │   ├── ArticleFilter.java                 # 인터페이스
│   │   ├── DateFilter.java                    # 날짜 필터
│   │   ├── QualityValidator.java              # 품질 검증
│   │   ├── DuplicateRemover.java              # 중복 제거
│   │   ├── CategoryClassifier.java            # 카테고리 분류
│   │   ├── RelevanceScorer.java               # 관련성 점수
│   │   └── DiversitySelector.java             # 다양성 선택
│   └── parser/
│       ├── RssParser.java                     # RSS 파싱
│       └── ArticleNormalizer.java             # 기사 정규화
│
├── analyzer/                                  # AI 분석 계층
│   ├── NewsAnalyzer.java                      # 인터페이스
│   ├── openai/
│   │   ├── OpenAiNewsAnalyzer.java            # OpenAI 분석기 구현체
│   │   ├── OpenAiClient.java                  # OpenAI API 클라이언트
│   │   ├── prompt/
│   │   │   ├── SystemPromptBuilder.java       # 시스템 프롬프트 구성
│   │   │   ├── AnalysisPromptBuilder.java     # 분석 요청 프롬프트 구성
│   │   │   └── AiResponseSchema.java          # AI 응답 DTO (Jackson)
│   │   └── util/
│   │       ├── BriefingBuilder.java            # AI 응답 → Briefing 변환
│   │       └── RetryTemplate.java             # 재시도 유틸 (Spring Retry 활용)
│   └── mock/
│       └── MockNewsAnalyzer.java              # Mock 분석기
│
├── publisher/                                 # 발행 계층
│   ├── BriefingPublisher.java                 # 인터페이스
│   ├── notion/
│   │   ├── NotionBriefingPublisher.java       # Notion 발행 구현체
│   │   ├── NotionClient.java                  # Notion API 클라이언트 (WebClient)
│   │   └── NotionPageBuilder.java             # Notion 블록 구성
│   ├── email/
│   │   └── EmailBriefingPublisher.java        # 이메일 발행 (향후 구현)
│   └── mock/
│       └── MockBriefingPublisher.java         # Mock 발행기
│
├── pipeline/                                  # 파이프라인 오케스트레이션
│   ├── BriefingPipeline.java                  # 메인 파이프라인 서비스
│   ├── PipelineLock.java                      # 잠금 인터페이스
│   ├── DbPipelineLock.java                    # DB 기반 잠금 구현체
│   ├── PipelineRecorder.java                  # 기록 인터페이스
│   ├── ExecutionTracker.java                  # 중복 실행 감지
│   └── PipelineDataValidator.java             # 파이프라인 데이터 검증
│
├── scheduler/                                 # 스케줄러
│   └── BriefingScheduler.java                 # @Scheduled 매시간 실행
│
├── admin/                                     # 관리자 REST API
│   ├── controller/
│   │   ├── StatusController.java              # 상태 조회 API
│   │   ├── RunController.java                 # 실행 제어 API
│   │   └── ItemController.java                # 아이템 조회 API
│   ├── dto/
│   │   ├── StatusOverviewResponse.java        # 상태 응답 DTO
│   │   ├── PipelineRunResponse.java           # 실행 응답 DTO
│   │   ├── PipelineRunListResponse.java       # 실행 목록 응답 DTO
│   │   ├── PipelineItemResponse.java          # 아이템 응답 DTO
│   │   └── TriggerRunRequest.java             # 실행 트리거 요청 DTO
│   └── security/
│       └── AdminTokenFilter.java              # Bearer 토큰 인증 필터
│
├── persistence/                               # DB 계층
│   ├── entity/
│   │   ├── PipelineRunEntity.java             # 파이프라인 실행 엔티티
│   │   ├── PipelineLogEntity.java             # 파이프라인 로그 엔티티
│   │   └── PipelineItemEntity.java            # 파이프라인 아이템 엔티티
│   └── repository/
│       ├── PipelineRunRepository.java         # JPA Repository
│       ├── PipelineLogRepository.java         # JPA Repository
│       └── PipelineItemRepository.java        # JPA Repository
│
├── config/                                    # 설정
│   ├── AppProperties.java                     # @ConfigurationProperties(prefix = "briefing")
│   ├── OpenAiProperties.java                  # @ConfigurationProperties(prefix = "openai")
│   ├── NotionProperties.java                  # @ConfigurationProperties(prefix = "notion")
│   ├── AdminProperties.java                   # @ConfigurationProperties(prefix = "admin")
│   ├── DatabaseProperties.java                # DB 설정 (Spring Boot 기본 활용)
│   ├── WebClientConfig.java                   # WebClient 빈 설정
│   ├── AsyncConfig.java                       # @EnableAsync 설정
│   ├── SchedulingConfig.java                  # @EnableScheduling 설정
│   └── SecurityConfig.java                    # Spring Security 설정
│
├── exception/                                 # 예외
│   ├── BriefingException.java                 # 기본 비즈니스 예외
│   ├── CollectException.java                  # 수집 단계 예외
│   ├── AnalyzeException.java                  # 분석 단계 예외
│   ├── PublishException.java                  # 발행 단계 예외
│   ├── PipelineException.java                 # 파이프라인 예외
│   ├── ErrorCode.java                         # enum (에러 코드)
│   └── GlobalExceptionHandler.java            # @RestControllerAdvice
│
└── util/                                      # 유틸리티
    ├── KstDateTimeUtil.java                   # KST 날짜/시간 유틸
    └── IdGenerator.java                       # ID 생성 유틸


src/main/resources/
├── application.yml                            # 기본 설정
├── application-dev.yml                        # 개발 프로파일
├── application-prod.yml                       # 운영 프로파일
├── application-test.yml                       # 테스트 프로파일
├── db/migration/                              # Flyway 마이그레이션 스크립트
│   └── V1__init_schema.sql                    # 초기 스키마
└── logback-spring.xml                         # Logback 설정


src/test/java/com/economicbriefing/
├── collector/
│   ├── DefaultNewsCollectorTest.java
│   ├── source/
│   │   └── AbstractRssSourceAdapterTest.java
│   └── filter/
│       ├── DateFilterTest.java
│       ├── QualityValidatorTest.java
│       ├── DuplicateRemoverTest.java
│       ├── CategoryClassifierTest.java
│       ├── RelevanceScorerTest.java
│       └── DiversitySelectorTest.java
├── analyzer/
│   └── openai/
│       ├── OpenAiNewsAnalyzerTest.java
│       └── BriefingBuilderTest.java
├── publisher/
│   └── notion/
│       └── NotionBriefingPublisherTest.java
├── pipeline/
│   ├── BriefingPipelineTest.java
│   └── BriefingPipelineIntegrationTest.java
├── admin/
│   ├── StatusControllerTest.java
│   ├── RunControllerTest.java
│   └── ItemControllerTest.java
├── scheduler/
│   └── BriefingSchedulerTest.java
└── util/
    └── KstDateTimeUtilTest.java
```

### 2.3 패키지 설명

| 패키지 | 역할 | Spring 컴포넌트 |
|--------|------|-----------------|
| `domain` | 순수 도메인 모델. 외부 프레임워크 의존성 없음 | 없음 (POJO) |
| `collector` | 뉴스 수집 + 필터링 + 파싱 | `@Service`, `@Component` |
| `analyzer` | AI 기반 뉴스 분석 | `@Service` |
| `publisher` | 브리핑 발행 (Notion, Email) | `@Service` |
| `pipeline` | 파이프라인 오케스트레이션 | `@Service` |
| `scheduler` | 스케줄러 (비즈니스 로직 없음, 서비스 호출만) | `@Component` |
| `admin` | 관리자 REST API | `@RestController` |
| `persistence` | DB 엔티티 + JPA Repository | `@Entity`, `@Repository` |
| `config` | 설정 클래스 | `@Configuration`, `@ConfigurationProperties` |
| `exception` | 예외 클래스 + 글로벌 핸들러 | `@RestControllerAdvice` |
| `util` | 유틸리티 | 없음 (static 메서드) |

---

## 3. Node.js → Java 파일 매핑

### 3.1 도메인 타입

| Node.js (TypeScript) | Java | 비고 |
|----------------------|------|------|
| `domain/article.ts` → Article | `domain/article/Article.java` | record 클래스 |
| `domain/article.ts` → NewsCategory | `domain/article/NewsCategory.java` | enum |
| `domain/article.ts` → ArticleSourceType | `domain/article/ArticleSourceType.java` | enum |
| `domain/article.ts` → CollectNewsRequest | `collector/dto/CollectNewsRequest.java` | record |
| `domain/article.ts` → CollectNewsResult | `collector/dto/CollectNewsResult.java` | record |
| `domain/article.ts` → SourceCollectionReport | `domain/article/SourceCollectionReport.java` | record |
| `domain/analyzedNews.ts` → AnalyzedNews | `domain/analysis/AnalyzedNews.java` | record |
| `domain/analyzedNews.ts` → EconomicTerm | `domain/analysis/EconomicTerm.java` | record |
| `domain/analyzedNews.ts` → SourceReference | `domain/analysis/SourceReference.java` | record |
| `domain/briefing.ts` → Briefing | `domain/briefing/Briefing.java` | record |
| `domain/briefing.ts` → PublishBriefingRequest | `publisher/dto/PublishBriefingRequest.java` | record |
| `domain/briefing.ts` → PublishBriefingResult | `publisher/dto/PublishBriefingResult.java` | record |
| `domain/execution.ts` → ExecutionLog | `domain/execution/ExecutionLog.java` | class |
| `domain/execution.ts` → ExecutionError | `domain/execution/ExecutionError.java` | record |

### 3.2 수집기

| Node.js | Java | 비고 |
|---------|------|------|
| `collectors/NewsCollector.ts` | `collector/NewsCollector.java` | 인터페이스 |
| `collectors/RealNewsCollector.ts` | `collector/DefaultNewsCollector.java` | `@Service` |
| `collectors/sources/SourceAdapter.ts` | `collector/source/SourceAdapter.java` | 인터페이스 |
| `collectors/sources/BaseRSSAdapter.ts` | `collector/source/AbstractRssSourceAdapter.java` | 추상 클래스 |
| `collectors/sources/YonhapSourceAdapter.ts` | `collector/source/YonhapSourceAdapter.java` | `@Component` |
| `collectors/sources/HankyungSourceAdapter.ts` | `collector/source/HankyungSourceAdapter.java` | `@Component` |
| `collectors/sources/MKSourceAdapter.ts` | `collector/source/MKSourceAdapter.java` | `@Component` |
| `collectors/sources/SBSBizSourceAdapter.ts` | `collector/source/SBSBizSourceAdapter.java` | `@Component` |
| `collectors/sources/SedailySourceAdapter.ts` | `collector/source/SedailySourceAdapter.java` | `@Component` |
| `collectors/sources/NewsisSourceAdapter.ts` | `collector/source/NewsisSourceAdapter.java` | `@Component` |
| `collectors/sources/MoneyTodaySourceAdapter.ts` | `collector/source/MoneyTodaySourceAdapter.java` | `@Component` |
| `collectors/sources/SegyeSourceAdapter.ts` | `collector/source/SegyeSourceAdapter.java` | `@Component` |
| `collectors/sources/KhanSourceAdapter.ts` | `collector/source/KhanSourceAdapter.java` | `@Component` |
| `collectors/sources/DongaSourceAdapter.ts` | `collector/source/DongaSourceAdapter.java` | `@Component` |
| `collectors/filters/dateFilter.ts` | `collector/filter/DateFilter.java` | `@Component` |
| `collectors/filters/qualityValidator.ts` | `collector/filter/QualityValidator.java` | `@Component` |
| `collectors/filters/duplicateRemover.ts` | `collector/filter/DuplicateRemover.java` | `@Component` |
| `collectors/filters/categoryClassifier.ts` | `collector/filter/CategoryClassifier.java` | `@Component` |
| `collectors/filters/relevanceScorer.ts` | `collector/filter/RelevanceScorer.java` | `@Component` |
| `collectors/filters/diversitySelector.ts` | `collector/filter/DiversitySelector.java` | `@Component` |
| `collectors/parsers/rssParser.ts` | `collector/parser/RssParser.java` | `@Component` |
| `collectors/parsers/articleNormalizer.ts` | `collector/parser/ArticleNormalizer.java` | `@Component` |
| `collectors/mock/MockNewsCollector.ts` | `collector/mock/MockNewsCollector.java` | 테스트용 |

### 3.3 분석기

| Node.js | Java | 비고 |
|---------|------|------|
| `analyzers/NewsAnalyzer.ts` | `analyzer/NewsAnalyzer.java` | 인터페이스 |
| `analyzers/openai/OpenAIClient.ts` | `analyzer/openai/OpenAiClient.java` | `@Component` |
| `analyzers/openai/OpenAINewsAnalyzer.ts` | `analyzer/openai/OpenAiNewsAnalyzer.java` | `@Service` |
| `analyzers/openai/prompts/systemPrompt.ts` | `analyzer/openai/prompt/SystemPromptBuilder.java` | `@Component` |
| `analyzers/openai/prompts/buildAnalysisPrompt.ts` | `analyzer/openai/prompt/AnalysisPromptBuilder.java` | `@Component` |
| `analyzers/openai/prompts/responseSchema.ts` | `analyzer/openai/prompt/AiResponseSchema.java` | DTO (Jackson) |
| `analyzers/openai/utils/buildBriefingFromAIResponse.ts` | `analyzer/openai/util/BriefingBuilder.java` | `@Component` |
| `analyzers/openai/utils/retryWithBackoff.ts` | Spring Retry 또는 커스텀 `RetryTemplate` | |
| `analyzers/mock/MockNewsAnalyzer.ts` | `analyzer/mock/MockNewsAnalyzer.java` | 테스트용 |

### 3.4 발행기

| Node.js | Java | 비고 |
|---------|------|------|
| `publishers/BriefingPublisher.ts` | `publisher/BriefingPublisher.java` | 인터페이스 |
| `publishers/notion/NotionBriefingPublisher.ts` | `publisher/notion/NotionBriefingPublisher.java` | `@Service` |
| `publishers/notion/NotionClientAdapter.ts` | `publisher/notion/NotionClient.java` | `@Component` (WebClient) |
| `publishers/notion/buildNotionPage.ts` | `publisher/notion/NotionPageBuilder.java` | `@Component` |
| `publishers/notion/notionTypes.ts` | `publisher/notion/dto/` 패키지 | Notion API DTO |
| `publishers/mock/MockBriefingPublisher.ts` | `publisher/mock/MockBriefingPublisher.java` | 테스트용 |

### 3.5 파이프라인 & 인프라

| Node.js | Java | 비고 |
|---------|------|------|
| `pipeline/PipelineLock.ts` | `pipeline/PipelineLock.java` | 인터페이스 |
| `pipeline/DbPipelineLock.ts` | `pipeline/DbPipelineLock.java` | `@Component` |
| `pipeline/PipelineRecorder.ts` | `pipeline/PipelineRecorder.java` | 인터페이스 |
| `pipeline/runPipeline.ts` | `pipeline/BriefingPipeline.java` | `@Service` |
| `app/runDailyBriefing.ts` | `pipeline/BriefingPipeline.java` | 통합 |
| `app/ExecutionTracker.ts` | `pipeline/ExecutionTracker.java` | `@Component` |
| `app/validatePipelineData.ts` | `pipeline/PipelineDataValidator.java` | `@Component` |
| `db/pool.ts` | Spring Boot DataSource (자동 설정) | |
| `db/migrate.ts` | Flyway 마이그레이션 | |
| `db/repositories/*.ts` | `persistence/repository/*.java` | JPA Repository |
| `config/env.ts` | `config/*Properties.java` | `@ConfigurationProperties` |
| `config/constants.ts` | `config/AppProperties.java` | 설정 파일로 이동 |
| `errors/AppError.ts` | `exception/BriefingException.java` | |
| `errors/errorCodes.ts` | `exception/ErrorCode.java` | enum |
| `utils/date.ts` | `util/KstDateTimeUtil.java` | `java.time` API |
| `utils/result.ts` | 불필요 (예외 기반 흐름) | |

### 3.6 관리자 대시보드

| Node.js | Java | 비고 |
|---------|------|------|
| `admin/server.ts` | Spring Boot 내장 (별도 설정 불필요) | |
| `admin/middleware/auth.ts` | `admin/security/AdminTokenFilter.java` | `OncePerRequestFilter` |
| `admin/middleware/errorHandler.ts` | `exception/GlobalExceptionHandler.java` | `@RestControllerAdvice` |
| `admin/routes/statusRoutes.ts` | `admin/controller/StatusController.java` | `@RestController` |
| `admin/routes/runRoutes.ts` | `admin/controller/RunController.java` | `@RestController` |
| `admin/routes/itemRoutes.ts` | `admin/controller/ItemController.java` | `@RestController` |

### 3.7 스케줄러

| Node.js | Java | 비고 |
|---------|------|------|
| `scheduler/schedulerOptions.ts` | `config/AppProperties.java` + CLI Runner | 설정으로 통합 |
| GitHub Actions 워크플로우 | 동일하게 유지 (빌드 명령만 변경) | |

---

## 4. Spring Boot 아키텍처

### 4.1 컴포넌트 다이어그램

```
┌──────────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                       │
│                                                                  │
│  ┌──────────────┐     ┌─────────────────────────────────────┐   │
│  │  Scheduler   │────▶│        BriefingPipeline              │   │
│  │  (@Scheduled)│     │        (@Service)                    │   │
│  └──────────────┘     │                                     │   │
│                       │  1. ExecutionTracker.checkDuplicate  │   │
│  ┌──────────────┐     │  2. NewsCollector.collect            │   │
│  │  Admin API   │────▶│  3. NewsAnalyzer.analyze             │   │
│  │  (Controller)│     │  4. BriefingPublisher.publish (각각) │   │
│  └──────────────┘     │  5. PipelineRecorder.record          │   │
│                       └──────────┬──────────────────────────┘   │
│                                  │                               │
│              ┌───────────────────┼───────────────────┐           │
│              ▼                   ▼                   ▼           │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐     │
│  │ NewsCollector   │  │ NewsAnalyzer   │  │ Publisher      │     │
│  │ (interface)     │  │ (interface)    │  │ (interface)    │     │
│  └───────┬────────┘  └───────┬────────┘  └───────┬────────┘     │
│          │                   │                   │               │
│          ▼                   ▼                   ▼               │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐     │
│  │ Default        │  │ OpenAi         │  │ Notion         │     │
│  │ NewsCollector  │  │ NewsAnalyzer   │  │ Publisher      │     │
│  │                │  │                │  ├────────────────┤     │
│  │ ┌────────────┐ │  │ ┌────────────┐ │  │ Email          │     │
│  │ │SourceAdapt.│ │  │ │OpenAiClient│ │  │ Publisher      │     │
│  │ │ (List)     │ │  │ │(WebClient) │ │  │                │     │
│  │ ├────────────┤ │  │ ├────────────┤ │  ├────────────────┤     │
│  │ │ Filters    │ │  │ │PromptBuild.│ │  │ Mock           │     │
│  │ │ (Chain)    │ │  │ │BriefingBld.│ │  │ Publisher      │     │
│  │ └────────────┘ │  │ └────────────┘ │  └────────────────┘     │
│  └────────────────┘  └────────────────┘                          │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                    Infrastructure                          │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │  │
│  │  │WebClient │  │PostgreSQL│  │Flyway    │  │Logback   │  │  │
│  │  │(HTTP)    │  │(JPA)     │  │(Migration│  │(Logging) │  │  │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘  │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

### 4.2 실행 흐름

```
BriefingScheduler (@Scheduled, 매시간)
│
▼
BriefingPipeline.execute(options)
│
├─ 1. PipelineLock.tryAcquire()
│     └─ 이미 실행 중이면 → 종료
│
├─ 2. ExecutionTracker.decide()
│     └─ 이미 성공한 briefing이면 → skip_already_published
│
├─ 3. PipelineRecorder.startRun()
│     └─ DB에 실행 기록 시작
│
├─ 4. NewsCollector.collect(request)
│     │
│     ├─ SourceAdapter.fetch() × 10 (병렬, CompletableFuture)
│     │   ├─ YonhapSourceAdapter
│     │   ├─ HankyungSourceAdapter
│     │   ├─ ... (8개 더)
│     │   └─ 각 어댑터: WebClient → RSS XML → Parse → Article[]
│     │
│     ├─ ArticleNormalizer.normalize()
│     │
│     └─ Filter Chain (순차 적용)
│         ├─ DateFilter.filter()
│         ├─ QualityValidator.validate()
│         ├─ DuplicateRemover.removeDuplicates()
│         ├─ CategoryClassifier.classify()
│         ├─ RelevanceScorer.score()
│         └─ DiversitySelector.select()
│
│     └─ 결과: CollectNewsResult (30-60 articles)
│
├─ 5. PipelineDataValidator.validateCollectionResult()
│
├─ 6. NewsAnalyzer.analyze(articles)
│     │
│     ├─ AnalysisPromptBuilder.build(articles)
│     ├─ OpenAiClient.chatCompletion(systemPrompt, userPrompt)
│     │   └─ WebClient → OpenAI API (gpt-4o, JSON mode)
│     │       └─ 재시도: 429, 500, 503 → RetryTemplate
│     ├─ Jackson으로 AiResponseSchema 파싱
│     └─ BriefingBuilder.build(response, articles)
│
│     └─ 결과: Briefing (5-10 analyzed news)
│
├─ 7. PipelineDataValidator.validateBriefing()
│
├─ 8. Publisher.publish(briefing) × N (순차)
│     │
│     ├─ NotionBriefingPublisher
│     │   ├─ NotionClient.queryDatabase() → 중복 확인
│     │   ├─ NotionPageBuilder.build(briefing) → 블록 생성
│     │   ├─ NotionClient.createPage() → 페이지 생성
│     │   └─ NotionClient.appendBlocks() → 나머지 블록 추가
│     │
│     └─ EmailBriefingPublisher (향후)
│
├─ 9. PipelineRecorder.finishRun(result)
│
└─ 10. PipelineLock.release()

└─ 결과: ExecutionLog (JSON 출력)
```

### 4.3 Spring Profile 전략

| Profile | 용도 | Collector | Analyzer | Publisher |
|---------|------|-----------|----------|-----------|
| `dev` | 로컬 개발 | MockNewsCollector | MockNewsAnalyzer | MockBriefingPublisher |
| `prod` | 운영 | DefaultNewsCollector | OpenAiNewsAnalyzer | NotionBriefingPublisher |
| `test` | 테스트 | Mock (DI) | Mock (DI) | Mock (DI) |

`@ConditionalOnProperty` 또는 `@Profile`로 구현체 자동 선택:

```java
@Service
@ConditionalOnProperty(name = "briefing.dry-run", havingValue = "false")
public class DefaultNewsCollector implements NewsCollector { ... }

@Service
@ConditionalOnProperty(name = "briefing.dry-run", havingValue = "true", matchIfMissing = true)
public class MockNewsCollector implements NewsCollector { ... }
```

---

## 5. Java 클래스 전체 설계

### 5.1 도메인 모델

#### `domain/article/Article.java`
```java
public record Article(
    String id,
    String title,
    String summary,
    String sourceName,
    ArticleSourceType sourceType,
    OffsetDateTime publishedAt,
    OffsetDateTime collectedAt,
    String url,
    List<NewsCategory> categories,
    String language,           // 항상 "ko"
    String content             // nullable
) {}
```

#### `domain/article/NewsCategory.java`
```java
public enum NewsCategory {
    INTEREST_RATE,
    DEPOSIT_SAVING,
    LOAN,
    HOUSING,
    JEONSE_MONTHLY_RENT,
    SUBSCRIPTION,
    TAX,
    PENSION,
    INSURANCE,
    COST_OF_LIVING,
    EXCHANGE_RATE,
    INVESTMENT,
    GOVERNMENT_SUPPORT,
    EMPLOYMENT_INCOME,
    HOUSEHOLD_DEBT,
    OTHER;

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static NewsCategory fromValue(String value) {
        return valueOf(value.toUpperCase());
    }
}
```

#### `domain/article/ArticleSourceType.java`
```java
public enum ArticleSourceType {
    NEWS_MEDIA,
    GOVERNMENT,
    PUBLIC_INSTITUTION,
    FINANCIAL_INSTITUTION,
    OTHER;
}
```

#### `domain/article/SourceCollectionReport.java`
```java
public record SourceCollectionReport(
    String sourceName,
    CollectionStatus status,     // SUCCESS, PARTIAL, FAILED
    int collectedCount,
    int acceptedCount,
    String errorCode,            // nullable
    String errorMessage,         // nullable
    Integer rawCount,            // nullable
    Integer dateFilteredCount,   // nullable
    Integer qualityPassedCount,  // nullable
    Integer deduplicatedCount,   // nullable
    Long durationMs              // nullable
) {
    public enum CollectionStatus { SUCCESS, PARTIAL, FAILED }
}
```

#### `domain/analysis/AnalyzedNews.java`
```java
public record AnalyzedNews(
    String id,
    String representativeTitle,
    NewsCategory category,
    int importance,                    // 1-5
    String whyImportant,
    TargetAudience targetAudience,    // nullable
    List<ImpactAssessment> impactAssessment,  // nullable
    String oneLineSummary,
    String explanation,
    NewsEvidenceStatus evidenceStatus,
    String uncertaintyNote,           // nullable
    List<EconomicTerm> economicTerms,
    List<SourceReference> sources
) {}
```

#### `domain/analysis/EconomicTerm.java`
```java
public record EconomicTerm(
    String term,
    String explanation,
    String example       // nullable
) {}
```

#### `domain/analysis/NewsEvidenceStatus.java`
```java
public enum NewsEvidenceStatus {
    CONFIRMED,
    PROPOSED,
    EXPECTED;
}
```

#### `domain/analysis/SourceReference.java`
```java
public record SourceReference(
    String articleId,
    String sourceName,
    String title,
    String url,
    OffsetDateTime publishedAt,
    boolean isPrimary
) {}
```

#### `domain/analysis/TargetAudience.java`
```java
public record TargetAudience(
    List<String> mustRead,
    List<String> notRelevant
) {}
```

#### `domain/analysis/ImpactAssessment.java`
```java
public record ImpactAssessment(
    String target,
    int score,        // 0-5
    String reason
) {}
```

#### `domain/briefing/Briefing.java`
```java
public record Briefing(
    String id,
    LocalDate targetDate,
    OffsetDateTime generatedAt,
    String title,
    List<String> overallSummary,
    List<AnalyzedNews> news,
    List<EconomicTerm> glossary,
    BriefingMetadata metadata
) {}
```

#### `domain/briefing/BriefingMetadata.java`
```java
public record BriefingMetadata(
    int collectedArticleCount,
    int analyzedArticleCount,
    int selectedNewsCount,
    String modelName,       // nullable (e.g., "gpt-4o")
    String promptVersion    // nullable (e.g., "v1")
) {}
```

#### `domain/execution/ExecutionLog.java`
```java
public class ExecutionLog {
    private final String executionId;
    private final LocalDate targetDate;
    private final OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private ExecutionStatus status;
    private int collectedArticleCount;
    private int selectedNewsCount;
    private final List<ExecutionError> errors = new ArrayList<>();

    // 생성자, getter, 상태 변경 메서드
}
```

#### `domain/execution/ExecutionError.java`
```java
public record ExecutionError(
    String stage,          // collect, analyze, publish, system
    String code,
    String message,
    boolean retryable,
    String sourceName      // nullable
) {}
```

#### `domain/execution/ExecutionStatus.java`
```java
public enum ExecutionStatus {
    RUNNING,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED;
}
```

#### `domain/execution/PublicationDecision.java`
```java
public enum PublicationDecision {
    PUBLISH,
    SKIP_ALREADY_PUBLISHED,
    RETRY_PREVIOUS_FAILURE;
}
```

---

### 5.2 수집기 (Collector)

#### `collector/NewsCollector.java`
```java
public interface NewsCollector {
    CollectNewsResult collect(CollectNewsRequest request);
}
```

#### `collector/dto/CollectNewsRequest.java`
```java
public record CollectNewsRequest(
    LocalDate targetDate,
    ZoneId timezone,            // 항상 Asia/Seoul
    Integer maxArticles,        // nullable
    OffsetDateTime startTime,   // nullable
    OffsetDateTime endTime      // nullable
) {
    public CollectNewsRequest {
        if (timezone == null) timezone = ZoneId.of("Asia/Seoul");
    }
}
```

#### `collector/dto/CollectNewsResult.java`
```java
public record CollectNewsResult(
    LocalDate targetDate,
    List<Article> articles,
    List<SourceCollectionReport> sourceReports,
    int totalCollected,
    int totalAccepted,
    int totalRejected
) {}
```

#### `collector/DefaultNewsCollector.java`
```java
@Service
@ConditionalOnProperty(name = "briefing.dry-run", havingValue = "false")
@RequiredArgsConstructor
public class DefaultNewsCollector implements NewsCollector {

    private final List<SourceAdapter> sourceAdapters;
    private final DateFilter dateFilter;
    private final QualityValidator qualityValidator;
    private final DuplicateRemover duplicateRemover;
    private final CategoryClassifier categoryClassifier;
    private final RelevanceScorer relevanceScorer;
    private final DiversitySelector diversitySelector;
    private final ArticleNormalizer articleNormalizer;

    @Override
    public CollectNewsResult collect(CollectNewsRequest request) {
        // 1. 모든 소스에서 병렬 수집 (CompletableFuture)
        // 2. 정규화
        // 3. 필터 체인 순차 적용
        // 4. CollectNewsResult 반환
    }
}
```

#### `collector/source/SourceAdapter.java`
```java
public interface SourceAdapter {
    String getSourceName();
    String getFeedUrl();
    ArticleSourceType getSourceType();
    List<NewsCategory> getDefaultCategories();
    List<Article> fetch();
}
```

#### `collector/source/AbstractRssSourceAdapter.java`
```java
@RequiredArgsConstructor
public abstract class AbstractRssSourceAdapter implements SourceAdapter {

    private final RssParser rssParser;
    private final ArticleNormalizer normalizer;

    @Override
    public List<Article> fetch() {
        // 1. rssParser.parse(getFeedUrl())
        // 2. normalizer.normalize(items, getSourceName(), getSourceType(), getDefaultCategories())
        // 3. 에러 시 빈 리스트 + 로그
    }
}
```

#### `collector/source/YonhapSourceAdapter.java` (대표 예시)
```java
@Component
public class YonhapSourceAdapter extends AbstractRssSourceAdapter {

    public YonhapSourceAdapter(RssParser rssParser, ArticleNormalizer normalizer) {
        super(rssParser, normalizer);
    }

    @Override
    public String getSourceName() { return "연합뉴스"; }

    @Override
    public String getFeedUrl() { return "https://www.yna.co.kr/rss/economy.xml"; }

    @Override
    public ArticleSourceType getSourceType() { return ArticleSourceType.NEWS_MEDIA; }

    @Override
    public List<NewsCategory> getDefaultCategories() { return List.of(NewsCategory.OTHER); }
}
```

> 나머지 9개 소스 어댑터도 동일한 패턴으로 구현. `sourceName`, `feedUrl`, `defaultCategories`만 다름.

#### `collector/filter/ArticleFilter.java`
```java
public interface ArticleFilter {
    List<Article> apply(List<Article> articles, CollectNewsRequest request);
}
```

#### `collector/filter/DateFilter.java`
```java
@Component
@RequiredArgsConstructor
public class DateFilter implements ArticleFilter {

    @Override
    public List<Article> apply(List<Article> articles, CollectNewsRequest request) {
        // startTime/endTime 기반 필터링
        // 없으면 targetDate 00:00:00 ~ 23:59:59 KST
    }
}
```

#### `collector/filter/QualityValidator.java`
```java
@Component
public class QualityValidator implements ArticleFilter {

    @Override
    public List<Article> apply(List<Article> articles, CollectNewsRequest request) {
        // title 비어있지 않은지
        // url 유효한지
        // publishedAt 파싱 가능한지
    }
}
```

#### `collector/filter/DuplicateRemover.java`
```java
@Component
public class DuplicateRemover implements ArticleFilter {

    @Override
    public List<Article> apply(List<Article> articles, CollectNewsRequest request) {
        // Phase 1: URL 정규화, ID, 같은 소스 제목 유사도
        // Phase 2: 교차 소스 이벤트 그룹핑
    }
}
```

#### `collector/filter/CategoryClassifier.java`
```java
@Component
public class CategoryClassifier implements ArticleFilter {

    // 카테고리별 키워드 맵 (한국어)
    private static final Map<NewsCategory, List<String>> CATEGORY_KEYWORDS = Map.of(...);
    private static final List<String> EXCLUDE_KEYWORDS = List.of(...);  // 연예, 스포츠

    @Override
    public List<Article> apply(List<Article> articles, CollectNewsRequest request) {
        // 제외 키워드 필터링
        // 카테고리 키워드 매칭 → categories 할당
    }
}
```

#### `collector/filter/RelevanceScorer.java`
```java
@Component
@RequiredArgsConstructor
public class RelevanceScorer {

    private final AppProperties appProperties;

    // 개인 재테크 관련 키워드별 점수
    public List<ScoredArticle> score(List<Article> articles) {
        // 키워드 매칭 → 점수 합산
        // MIN_PERSONAL_FINANCE_RELEVANCE 이상만 반환
    }

    public record ScoredArticle(Article article, int score) {}
}
```

#### `collector/filter/DiversitySelector.java`
```java
@Component
@RequiredArgsConstructor
public class DiversitySelector {

    private final AppProperties appProperties;

    public List<Article> select(List<RelevanceScorer.ScoredArticle> scoredArticles) {
        // 소스별 MAX_ARTICLES_PER_SOURCE 하드캡
        // 카테고리별 MAX_ARTICLES_PER_CATEGORY 소프트캡
        // 고점수 기사 SOFT_MAX_OVERRIDE_SCORE 오버라이드
    }
}
```

#### `collector/parser/RssParser.java`
```java
@Component
@RequiredArgsConstructor
public class RssParser {

    private final WebClient webClient;
    private final AppProperties appProperties;

    public List<RssItem> parse(String feedUrl) {
        // WebClient로 RSS XML 가져오기
        // JDOM2 또는 Rome 라이브러리로 RSS 파싱
        // timeout: appProperties.getTimeouts().getRssHttp()
    }

    public record RssItem(
        String title, String link, String pubDate,
        String description, String content, String guid
    ) {}
}
```

#### `collector/parser/ArticleNormalizer.java`
```java
@Component
public class ArticleNormalizer {

    public Article normalize(
        RssParser.RssItem item,
        String sourceName,
        ArticleSourceType sourceType,
        List<NewsCategory> defaultCategories
    ) {
        // RssItem → Article 변환
        // ID 생성 (소스명 + guid 또는 URL 해시)
        // 날짜 파싱 (KST 변환)
        // language: "ko"
    }
}
```

---

### 5.3 분석기 (Analyzer)

#### `analyzer/NewsAnalyzer.java`
```java
public interface NewsAnalyzer {
    Briefing analyze(AnalyzeNewsRequest request);
}
```

#### `analyzer/dto/AnalyzeNewsRequest.java`
```java
public record AnalyzeNewsRequest(
    LocalDate targetDate,
    List<Article> articles,
    int maxSelectedNews     // 기본 10
) {}
```

#### `analyzer/openai/OpenAiNewsAnalyzer.java`
```java
@Service
@ConditionalOnProperty(name = "openai.api-key")
@RequiredArgsConstructor
public class OpenAiNewsAnalyzer implements NewsAnalyzer {

    private final OpenAiClient openAiClient;
    private final SystemPromptBuilder systemPromptBuilder;
    private final AnalysisPromptBuilder analysisPromptBuilder;
    private final BriefingBuilder briefingBuilder;

    @Override
    public Briefing analyze(AnalyzeNewsRequest request) {
        String systemPrompt = systemPromptBuilder.build();
        String userPrompt = analysisPromptBuilder.build(request);
        AiResponseSchema response = openAiClient.chatCompletion(systemPrompt, userPrompt);
        return briefingBuilder.build(response, request);
    }
}
```

#### `analyzer/openai/OpenAiClient.java`
```java
@Component
@RequiredArgsConstructor
public class OpenAiClient {

    private final WebClient openAiWebClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    @Retryable(
        retryFor = {WebClientResponseException.TooManyRequests.class, WebClientResponseException.ServiceUnavailable.class},
        maxAttempts = 2,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public AiResponseSchema chatCompletion(String systemPrompt, String userPrompt) {
        // WebClient POST to OpenAI Chat Completions API
        // model: gpt-4o
        // temperature: 0.3
        // response_format: json_object
        // timeout: properties.getTimeout()
        // Jackson으로 응답 파싱
    }
}
```

#### `analyzer/openai/prompt/SystemPromptBuilder.java`
```java
@Component
public class SystemPromptBuilder {

    public String build() {
        // 129줄 한국어 시스템 프롬프트 반환
        // 현재 Node.js의 systemPrompt.ts 내용 그대로 유지
    }
}
```

#### `analyzer/openai/prompt/AnalysisPromptBuilder.java`
```java
@Component
@RequiredArgsConstructor
public class AnalysisPromptBuilder {

    private final AppProperties appProperties;

    public String build(AnalyzeNewsRequest request) {
        // targetDate, maxSelectedNews, articles 정보로
        // 분석 요청 프롬프트 구성
        // 대상 독자 프로필 포함
    }
}
```

#### `analyzer/openai/prompt/AiResponseSchema.java`
```java
// AI 응답을 Jackson으로 역직렬화하기 위한 DTO
public record AiResponseSchema(
    List<String> overallSummary,
    List<AiNewsItem> news,
    List<AiGlossaryItem> glossary       // nullable
) {
    public record AiNewsItem(
        String id,
        String representativeTitle,
        String category,
        int importance,
        String whyImportant,
        AiTargetAudience targetAudience,  // nullable
        List<AiImpactAssessment> impactAssessment,  // nullable
        String oneLineSummary,
        String explanation,
        String evidenceStatus,
        String uncertaintyNote,           // nullable
        List<AiEconomicTerm> economicTerms,  // nullable
        List<AiSource> sources
    ) {}

    public record AiTargetAudience(List<String> mustRead, List<String> notRelevant) {}
    public record AiImpactAssessment(String target, int score, String reason) {}
    public record AiEconomicTerm(String term, String explanation, String example) {}
    public record AiGlossaryItem(String term, String explanation, String example) {}
    public record AiSource(String articleId, boolean isPrimary) {}
}
```

#### `analyzer/openai/util/BriefingBuilder.java`
```java
@Component
public class BriefingBuilder {

    public Briefing build(AiResponseSchema response, AnalyzeNewsRequest request) {
        // AiResponseSchema → Briefing 도메인 객체 변환
        // ID 생성: "briefing-{targetDate}" 또는 "briefing-{targetDate}T{hour}:00"
        // title 생성: "{targetDate} 경제 브리핑"
        // sources 매핑: articleId로 원본 Article 참조
        // metadata 구성
    }
}
```

#### `analyzer/mock/MockNewsAnalyzer.java`
```java
@Service
@ConditionalOnProperty(name = "openai.api-key", matchIfMissing = true, havingValue = "")
public class MockNewsAnalyzer implements NewsAnalyzer {
    // 하드코딩된 3개 분석 결과 반환
}
```

---

### 5.4 발행기 (Publisher)

#### `publisher/BriefingPublisher.java`
```java
public interface BriefingPublisher {
    PublishChannelResult publish(PublishBriefingRequest request);
    String getChannelName();
}
```

#### `publisher/dto/PublishBriefingRequest.java`
```java
public record PublishBriefingRequest(
    Briefing briefing,
    boolean dryRun
) {}
```

#### `publisher/dto/PublishBriefingResult.java`
```java
public record PublishBriefingResult(
    String briefingId,
    List<PublishChannelResult> results,
    OffsetDateTime completedAt
) {}
```

#### `publisher/dto/PublishChannelResult.java`
```java
public record PublishChannelResult(
    String channel,       // "notion", "email", "mock"
    PublishStatus status,
    String externalId,    // nullable (e.g., Notion page ID)
    String errorCode,     // nullable
    String errorMessage   // nullable
) {
    public enum PublishStatus { SUCCESS, SKIPPED, FAILED }
}
```

#### `publisher/notion/NotionBriefingPublisher.java`
```java
@Service
@ConditionalOnProperty(name = "notion.api-key")
@RequiredArgsConstructor
public class NotionBriefingPublisher implements BriefingPublisher {

    private final NotionClient notionClient;
    private final NotionPageBuilder notionPageBuilder;

    @Override
    public String getChannelName() { return "notion"; }

    @Override
    public PublishChannelResult publish(PublishBriefingRequest request) {
        // 1. 중복 확인 (queryDatabase)
        // 2. 페이지 생성 (createPage)
        // 3. 나머지 블록 추가 (appendBlocks, 100개씩)
        // 4. 결과 반환
    }
}
```

#### `publisher/notion/NotionClient.java`
```java
@Component
@RequiredArgsConstructor
public class NotionClient {

    private final WebClient notionWebClient;
    private final NotionProperties properties;
    private final ObjectMapper objectMapper;

    public boolean pageExists(String briefingId) { ... }
    public String createPage(Map<String, Object> pagePayload) { ... }
    public void appendBlocks(String pageId, List<Map<String, Object>> blocks) { ... }
}
```

#### `publisher/notion/NotionPageBuilder.java`
```java
@Component
public class NotionPageBuilder {

    public Map<String, Object> buildPageProperties(Briefing briefing) {
        // Name, Briefing ID, Target Date, Generated At, News Count 속성 구성
    }

    public List<Map<String, Object>> buildContentBlocks(Briefing briefing) {
        // 오늘의 핵심 요약
        // 주요 뉴스 (각 뉴스별 제목, 요약, 설명, 경제용어, 출처)
        // 경제용어 사전
        // 브리핑 정보
    }
}
```

#### `publisher/mock/MockBriefingPublisher.java`
```java
@Service
@ConditionalOnProperty(name = "briefing.dry-run", havingValue = "true", matchIfMissing = true)
public class MockBriefingPublisher implements BriefingPublisher {
    // 인메모리 저장
}
```

---

### 5.5 파이프라인

#### `pipeline/BriefingPipeline.java`
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class BriefingPipeline {

    private final NewsCollector newsCollector;
    private final NewsAnalyzer newsAnalyzer;
    private final List<BriefingPublisher> publishers;
    private final ExecutionTracker executionTracker;
    private final PipelineDataValidator validator;
    private final PipelineLock pipelineLock;
    private final PipelineRecorder pipelineRecorder;

    public ExecutionLog execute(PipelineOptions options) {
        // 1. tryAcquire lock
        // 2. decide (skip/retry/publish)
        // 3. collect
        // 4. validate collection
        // 5. analyze
        // 6. validate briefing
        // 7. publish (각 publisher 순차 호출)
        // 8. record execution
        // 9. release lock
        // 10. return ExecutionLog
    }
}
```

#### `pipeline/PipelineOptions.java`
```java
public record PipelineOptions(
    LocalDate targetDate,
    OffsetDateTime startTime,    // nullable
    OffsetDateTime endTime,      // nullable
    boolean dryRun,
    String mode                  // "automatic" | "manual"
) {}
```

#### `pipeline/PipelineLock.java`
```java
public interface PipelineLock {
    boolean tryAcquire(String runId);
    void release(String runId);
}
```

#### `pipeline/DbPipelineLock.java`
```java
@Component
@RequiredArgsConstructor
public class DbPipelineLock implements PipelineLock {
    private final PipelineRunRepository pipelineRunRepository;
    private final AppProperties appProperties;
    // DB 기반 잠금 구현
}
```

#### `pipeline/PipelineRecorder.java`
```java
public interface PipelineRecorder {
    void startRun(String runId, String triggerType);
    void updateStep(String runId, String step);
    void logMessage(String runId, String level, String step, String message);
    void finishRun(String runId, ExecutionLog log);
}
```

#### `pipeline/ExecutionTracker.java`
```java
@Component
public class ExecutionTracker {
    // 동일 날짜/시간대에 이미 성공한 실행이 있는지 확인
    public PublicationDecision decide(LocalDate targetDate, OffsetDateTime startTime) { ... }
    public void recordExecution(ExecutionLog log) { ... }
}
```

#### `pipeline/PipelineDataValidator.java`
```java
@Component
public class PipelineDataValidator {
    public void validateCollectionResult(CollectNewsResult result) { ... }
    public void validateBriefing(Briefing briefing) { ... }
}
```

---

### 5.6 스케줄러

#### `scheduler/BriefingScheduler.java`
```java
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "briefing.scheduler.enabled", havingValue = "true")
public class BriefingScheduler {

    private final BriefingPipeline briefingPipeline;
    private final AppProperties appProperties;

    @Scheduled(cron = "${briefing.scheduler.cron:0 0 * * * *}")
    public void executeHourlyBriefing() {
        log.info("스케줄러 실행 시작");

        PipelineOptions options = PipelineOptions.forAutomatic(
            KstDateTimeUtil.getCurrentDate(),
            KstDateTimeUtil.getPreviousHourRange()
        );

        try {
            ExecutionLog result = briefingPipeline.execute(options);
            log.info("스케줄러 실행 완료: status={}, selectedNews={}",
                result.getStatus(), result.getSelectedNewsCount());
        } catch (Exception e) {
            log.error("스케줄러 실행 실패", e);
        }
    }
}
```

---

### 5.7 관리자 API

#### `admin/controller/StatusController.java`
```java
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class StatusController {

    private final PipelineRunRepository runRepository;

    @GetMapping("/status/overview")
    public StatusOverviewResponse getOverview() { ... }
}
```

#### `admin/controller/RunController.java`
```java
@RestController
@RequestMapping("/api/admin/runs")
@RequiredArgsConstructor
public class RunController {

    private final PipelineRunRepository runRepository;
    private final BriefingPipeline briefingPipeline;

    @GetMapping
    public PipelineRunListResponse listRuns(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) { ... }

    @GetMapping("/{id}")
    public PipelineRunResponse getRun(@PathVariable String id) { ... }

    @PostMapping("/trigger")
    public PipelineRunResponse triggerRun(@RequestBody TriggerRunRequest request) { ... }

    @PostMapping("/{id}/cancel")
    public void cancelRun(@PathVariable String id) { ... }
}
```

#### `admin/controller/ItemController.java`
```java
@RestController
@RequestMapping("/api/admin/items")
@RequiredArgsConstructor
public class ItemController {

    private final PipelineItemRepository itemRepository;

    @GetMapping
    public Page<PipelineItemResponse> listItems(...) { ... }

    @GetMapping("/{id}")
    public PipelineItemResponse getItem(@PathVariable String id) { ... }
}
```

#### `admin/security/AdminTokenFilter.java`
```java
@Component
@RequiredArgsConstructor
public class AdminTokenFilter extends OncePerRequestFilter {

    private final AdminProperties adminProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) {
        // /api/admin/** 요청에 대해 Bearer 토큰 검증
        // adminProperties.getToken()과 비교
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin");
    }
}
```

---

### 5.8 설정

#### `config/AppProperties.java`
```java
@ConfigurationProperties(prefix = "briefing")
@Validated
public record AppProperties(
    boolean dryRun,
    TimeoutProperties timeouts,
    RetryProperties retry,
    DiversityProperties diversity,
    AudienceProperties audience,
    SchedulerProperties scheduler
) {
    public record TimeoutProperties(
        Duration rssHttp,       // 10s
        Duration aiApi,         // 60s
        Duration notionApi,     // 15s
        Duration emailApi       // 15s
    ) {}

    public record RetryProperties(
        int maxAttempts,            // 2
        Duration initialDelay,     // 1s
        Duration nextDelay         // 2s
    ) {}

    public record DiversityProperties(
        int maxArticlesPerSource,       // 3
        int maxArticlesPerCategory,     // 3
        int minPersonalFinanceRelevance, // 3
        int softMaxOverrideScore         // 5
    ) {}

    public record AudienceProperties(
        String economicKnowledgeLevel,    // "beginner"
        List<String> interests,
        List<String> contextNotes
    ) {}

    public record SchedulerProperties(
        boolean enabled,
        String cron              // "0 0 * * * *"
    ) {}
}
```

#### `config/OpenAiProperties.java`
```java
@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
    String apiKey,
    String model,           // "gpt-4o"
    double temperature,     // 0.3
    Duration timeout,       // 60s
    int maxSelectedNews     // 10
) {}
```

#### `config/NotionProperties.java`
```java
@ConfigurationProperties(prefix = "notion")
public record NotionProperties(
    String apiKey,
    String databaseId,
    Duration timeout        // 15s
) {}
```

#### `config/AdminProperties.java`
```java
@ConfigurationProperties(prefix = "admin")
public record AdminProperties(
    String token,
    int port,               // 3000
    int defaultPageSize     // 20
) {}
```

#### `config/WebClientConfig.java`
```java
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient rssWebClient(AppProperties appProperties) {
        return WebClient.builder()
            .codecs(config -> config.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
            .build();
    }

    @Bean
    public WebClient openAiWebClient(OpenAiProperties properties) {
        return WebClient.builder()
            .baseUrl("https://api.openai.com/v1")
            .defaultHeader("Authorization", "Bearer " + properties.apiKey())
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    @Bean
    public WebClient notionWebClient(NotionProperties properties) {
        return WebClient.builder()
            .baseUrl("https://api.notion.com/v1")
            .defaultHeader("Authorization", "Bearer " + properties.apiKey())
            .defaultHeader("Notion-Version", "2022-06-28")
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}
```

#### `config/AsyncConfig.java`
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    // RSS 병렬 수집용 Executor 설정
    @Bean
    public Executor rssCollectorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("rss-collector-");
        executor.initialize();
        return executor;
    }
}
```

#### `config/SchedulingConfig.java`
```java
@Configuration
@EnableScheduling
public class SchedulingConfig { }
```

---

### 5.9 예외

#### `exception/ErrorCode.java`
```java
public enum ErrorCode {

    // Collect
    COLLECT_SOURCE_TIMEOUT("RSS feed timeout", true),
    COLLECT_SOURCE_UNAVAILABLE("Feed unreachable", true),
    COLLECT_PARSE_ERROR("RSS parsing failed", false),
    COLLECT_NO_ARTICLES("No articles after filtering", false),

    // Analyze
    ANALYZE_API_ERROR("OpenAI API error", true),
    ANALYZE_VALIDATION_ERROR("Response schema invalid", false),
    ANALYZE_TIMEOUT("OpenAI API timeout", true),
    ANALYZE_EMPTY_INPUT("No articles to analyze", false),

    // Publish
    PUBLISH_CHANNEL_ERROR("Channel publish failed", true),
    PUBLISH_ALL_CHANNELS_FAILED("All channels failed", false),
    PUBLISH_DUPLICATE("Briefing already exists", false),

    // System
    SYSTEM_CONFIG_ERROR("Config validation failed", false),
    SYSTEM_UNEXPECTED("Unexpected error", false),

    // Pipeline
    PIPELINE_ALREADY_RUNNING("Pipeline already running", false),
    RUN_NOT_FOUND("Pipeline run not found", false),
    ITEM_NOT_FOUND("Item not found", false),
    UNAUTHORIZED("Auth token invalid", false),
    DB_CONNECTION_ERROR("DB connection failed", true);

    private final String message;
    private final boolean retryable;

    ErrorCode(String message, boolean retryable) {
        this.message = message;
        this.retryable = retryable;
    }

    public String getMessage() { return message; }
    public boolean isRetryable() { return retryable; }
}
```

#### `exception/BriefingException.java`
```java
public class BriefingException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String stage;

    public BriefingException(ErrorCode errorCode, String stage) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.stage = stage;
    }

    public BriefingException(ErrorCode errorCode, String stage, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.stage = stage;
    }

    // getter
}
```

#### `exception/CollectException.java`
```java
public class CollectException extends BriefingException {
    private final String sourceName;

    public CollectException(ErrorCode errorCode, String sourceName) {
        super(errorCode, "collect");
        this.sourceName = sourceName;
    }

    public CollectException(ErrorCode errorCode, String sourceName, Throwable cause) {
        super(errorCode, "collect", cause);
        this.sourceName = sourceName;
    }
}
```

#### `exception/AnalyzeException.java`
```java
public class AnalyzeException extends BriefingException {
    public AnalyzeException(ErrorCode errorCode) { super(errorCode, "analyze"); }
    public AnalyzeException(ErrorCode errorCode, Throwable cause) { super(errorCode, "analyze", cause); }
}
```

#### `exception/PublishException.java`
```java
public class PublishException extends BriefingException {
    private final String channel;

    public PublishException(ErrorCode errorCode, String channel) {
        super(errorCode, "publish");
        this.channel = channel;
    }
}
```

#### `exception/GlobalExceptionHandler.java`
```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BriefingException.class)
    public ResponseEntity<ErrorResponse> handleBriefingException(BriefingException e) {
        log.error("Business error: stage={}, code={}", e.getStage(), e.getErrorCode(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse(e.getErrorCode().name(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("SYSTEM_UNEXPECTED", e.getMessage()));
    }

    public record ErrorResponse(String code, String message) {}
}
```

---

### 5.10 유틸리티

#### `util/KstDateTimeUtil.java`
```java
public final class KstDateTimeUtil {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final int KST_OFFSET_HOURS = 9;

    private KstDateTimeUtil() {}

    public static OffsetDateTime now() {
        return OffsetDateTime.now(KST);
    }

    public static LocalDate getCurrentDate() {
        return LocalDate.now(KST);
    }

    public static TimeRange getPreviousHourRange() {
        OffsetDateTime now = now();
        OffsetDateTime startOfPreviousHour = now.truncatedTo(ChronoUnit.HOURS).minusHours(1);
        OffsetDateTime endOfPreviousHour = startOfPreviousHour.plusHours(1).minusNanos(1);
        return new TimeRange(startOfPreviousHour, endOfPreviousHour);
    }

    public static TimeRange getFullDayRange(LocalDate date) {
        OffsetDateTime start = date.atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime end = start.plusDays(1).minusNanos(1);
        return new TimeRange(start, end);
    }

    public record TimeRange(OffsetDateTime start, OffsetDateTime end) {}
}
```

#### `util/IdGenerator.java`
```java
public final class IdGenerator {

    private IdGenerator() {}

    public static String executionId() {
        return "exec-" + System.currentTimeMillis();
    }

    public static String briefingId(LocalDate targetDate) {
        return "briefing-" + targetDate;
    }

    public static String briefingId(LocalDate targetDate, int hour) {
        return "briefing-" + targetDate + "T" + String.format("%02d", hour) + ":00";
    }

    public static String articleId(String sourceName, String guid) {
        // 소스명 + guid로 안정적 ID 생성
        return sourceName.hashCode() + "-" + guid.hashCode();
    }
}
```

---

### 5.11 DB 엔티티 & Repository

#### `persistence/entity/PipelineRunEntity.java`
```java
@Entity
@Table(name = "pipeline_runs")
@Getter @Setter
@NoArgsConstructor
public class PipelineRunEntity {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    private RunStatus status;

    private String triggerType;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private Integer durationMs;
    private String currentStep;
    private int collectedCount;
    private int duplicateCount;
    private int analysisSuccessCount;
    private int analysisFailureCount;
    private int publishSuccessCount;
    private int publishFailureCount;
    private int totalFailureCount;
    private String errorCode;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public enum RunStatus { RUNNING, SUCCESS, PARTIAL_SUCCESS, FAILED }

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
```

#### `persistence/entity/PipelineLogEntity.java`
```java
@Entity
@Table(name = "pipeline_logs")
@Getter @Setter
@NoArgsConstructor
public class PipelineLogEntity {

    @Id
    private String id;
    private String runId;

    @Enumerated(EnumType.STRING)
    private LogLevel level;

    private String step;

    @Column(columnDefinition = "TEXT")
    private String message;

    private OffsetDateTime timestamp;

    public enum LogLevel { INFO, WARN, ERROR }
}
```

#### `persistence/entity/PipelineItemEntity.java`
```java
@Entity
@Table(name = "pipeline_items")
@Getter @Setter
@NoArgsConstructor
public class PipelineItemEntity {

    @Id
    private String id;
    private String runId;
    private String articleId;

    @Enumerated(EnumType.STRING)
    private ItemStatus status;

    private String duplicateOf;
    private String errorCode;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public enum ItemStatus { COLLECTED, ANALYZED, PUBLISHED, FAILED }
}
```

#### `persistence/repository/PipelineRunRepository.java`
```java
public interface PipelineRunRepository extends JpaRepository<PipelineRunEntity, String> {
    List<PipelineRunEntity> findByStatus(PipelineRunEntity.RunStatus status);
    Optional<PipelineRunEntity> findFirstByStatusOrderByStartedAtDesc(PipelineRunEntity.RunStatus status);
    Page<PipelineRunEntity> findAllByOrderByStartedAtDesc(Pageable pageable);
}
```

#### `persistence/repository/PipelineLogRepository.java`
```java
public interface PipelineLogRepository extends JpaRepository<PipelineLogEntity, String> {
    List<PipelineLogEntity> findByRunIdOrderByTimestampAsc(String runId);
}
```

#### `persistence/repository/PipelineItemRepository.java`
```java
public interface PipelineItemRepository extends JpaRepository<PipelineItemEntity, String> {
    List<PipelineItemEntity> findByRunId(String runId);
    Page<PipelineItemEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
```

---

### 5.12 application.yml

```yaml
# application.yml (기본)
spring:
  application:
    name: economic-briefing
  datasource:
    url: jdbc:postgresql://localhost:5432/economic_briefing
    username: postgres
    password: ""
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: ${ADMIN_PORT:3000}

briefing:
  dry-run: ${DRY_RUN:true}
  timeouts:
    rss-http: 10s
    ai-api: 60s
    notion-api: 15s
    email-api: 15s
  retry:
    max-attempts: 2
    initial-delay: 1s
    next-delay: 2s
  diversity:
    max-articles-per-source: 3
    max-articles-per-category: 3
    min-personal-finance-relevance: 3
    soft-max-override-score: 5
  audience:
    economic-knowledge-level: beginner
    interests:
      - interest_rate
      - loan
      - housing
      - jeonse_monthly_rent
      - deposit_saving
      - government_support
    context-notes:
      - 신혼부부
      - 주택 구입과 출산 준비
      - 경제용어 설명 필요
  scheduler:
    enabled: ${SCHEDULER_ENABLED:false}
    cron: "0 0 * * * *"

openai:
  api-key: ${OPENAI_API_KEY:}
  model: gpt-4o
  temperature: 0.3
  timeout: 60s
  max-selected-news: 10

notion:
  api-key: ${NOTION_API_KEY:}
  database-id: ${NOTION_DATABASE_ID:}
  timeout: 15s

admin:
  token: ${ADMIN_TOKEN:}
  port: ${ADMIN_PORT:3000}
  default-page-size: 20

logging:
  level:
    root: INFO
    com.economicbriefing: ${LOG_LEVEL:INFO}
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

```yaml
# application-dev.yml
briefing:
  dry-run: true
  scheduler:
    enabled: false

spring:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true

logging:
  level:
    com.economicbriefing: DEBUG
```

```yaml
# application-prod.yml
briefing:
  dry-run: false
  scheduler:
    enabled: true

spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

```yaml
# application-test.yml
briefing:
  dry-run: true
  scheduler:
    enabled: false

spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
  flyway:
    enabled: false
```

---

### 5.13 build.gradle.kts

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.economicbriefing"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")     // WebClient
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // RSS Parsing
    implementation("com.rometools:rome:2.1.0")

    // Jackson (기본 포함, 추가 모듈)
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Retry
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework:spring-aspects")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

---

## 6. 마이그레이션 계획

### 단계 개요

| 단계 | 내용 | 산출물 |
|------|------|--------|
| 1단계 | 프로젝트 기반 구축 | Gradle, Spring Boot, 설정, 도메인 모델 |
| 2단계 | 뉴스 수집기 | RSS 파싱, 필터 체인, 소스 어댑터 |
| 3단계 | AI 분석기 | OpenAI 연동, 프롬프트, 응답 처리 |
| 4단계 | 발행기 | Notion 발행, Mock 발행 |
| 5단계 | 파이프라인 오케스트레이션 | 파이프라인, 잠금, 기록, 실행 추적 |
| 6단계 | 관리자 대시보드 | REST API, 인증, DB 엔티티 |
| 7단계 | 스케줄러 & CLI | 매시간 실행, CLI 러너 |
| 8단계 | GitHub Actions | 워크플로우 수정, Watchdog |
| 9단계 | 테스트 & 검증 | 단위/통합 테스트, 전체 파이프라인 검증 |

---

### 1단계: 프로젝트 기반 구축

**목표:** Spring Boot 프로젝트 생성, 기본 설정, 도메인 모델 정의

**작업 내용:**
1. Spring Initializr로 프로젝트 생성 (또는 수동)
2. `build.gradle.kts` 작성 (위 5.13 참고)
3. 패키지 구조 생성
4. `application.yml`, `application-dev.yml`, `application-prod.yml`, `application-test.yml` 작성
5. `AppProperties`, `OpenAiProperties`, `NotionProperties`, `AdminProperties` 작성
6. 도메인 모델 전체 작성 (Article, AnalyzedNews, Briefing, ExecutionLog 등)
7. `ErrorCode`, `BriefingException` 및 하위 예외 클래스 작성
8. `KstDateTimeUtil`, `IdGenerator` 작성
9. Flyway 마이그레이션 스크립트 `V1__init_schema.sql` 작성
10. `EconomicBriefingApplication.java` 메인 클래스

**검증:**
- `./gradlew build` 성공
- 도메인 모델 단위 테스트 통과

---

### 2단계: 뉴스 수집기

**목표:** 10개 RSS 소스에서 뉴스를 수집하고 다단계 필터링 적용

**작업 내용:**
1. `NewsCollector` 인터페이스
2. `SourceAdapter` 인터페이스, `AbstractRssSourceAdapter` 추상 클래스
3. 10개 RSS 소스 어댑터 구현 (각 `@Component`)
4. `RssParser` (Rome 라이브러리 사용) + `ArticleNormalizer`
5. 필터 체인:
   - `DateFilter`
   - `QualityValidator`
   - `DuplicateRemover` (URL 정규화, 제목 유사도)
   - `CategoryClassifier` (한국어 키워드 맵)
   - `RelevanceScorer` (개인 재테크 점수)
   - `DiversitySelector` (소스/카테고리 캡)
6. `DefaultNewsCollector` (`CompletableFuture`로 병렬 수집)
7. `MockNewsCollector` (테스트용)
8. `WebClientConfig` - RSS용 WebClient 빈

**검증:**
- 각 필터 단위 테스트 통과
- Mock 수집기 테스트 통과
- 실제 RSS 1개 소스 수집 수동 테스트

---

### 3단계: AI 분석기

**목표:** OpenAI GPT-4o API로 수집된 기사 분석

**작업 내용:**
1. `NewsAnalyzer` 인터페이스
2. `OpenAiClient` (WebClient + Spring Retry)
3. `SystemPromptBuilder` (129줄 한국어 프롬프트 이식)
4. `AnalysisPromptBuilder` (분석 요청 프롬프트)
5. `AiResponseSchema` (Jackson DTO)
6. `BriefingBuilder` (AI 응답 → Briefing 변환)
7. `OpenAiNewsAnalyzer` (조합)
8. `MockNewsAnalyzer` (하드코딩 응답)
9. `WebClientConfig` - OpenAI용 WebClient 빈

**검증:**
- Mock 분석기 단위 테스트 통과
- `AiResponseSchema` Jackson 역직렬화 테스트
- (선택) 실제 OpenAI API 수동 테스트

---

### 4단계: 발행기

**목표:** 분석된 브리핑을 Notion에 발행

**작업 내용:**
1. `BriefingPublisher` 인터페이스
2. `NotionClient` (WebClient로 Notion API 호출)
3. `NotionPageBuilder` (브리핑 → Notion 블록 변환)
4. `NotionBriefingPublisher` (중복 확인 + 페이지 생성)
5. `MockBriefingPublisher` (인메모리)
6. `WebClientConfig` - Notion용 WebClient 빈

**검증:**
- Mock 발행기 테스트 통과
- `NotionPageBuilder` 단위 테스트 (블록 구조 검증)
- (선택) 실제 Notion API 수동 테스트

---

### 5단계: 파이프라인 오케스트레이션

**목표:** 수집→분석→발행 전체 흐름 연결

**작업 내용:**
1. `BriefingPipeline` (메인 서비스)
2. `PipelineLock` 인터페이스 + `DbPipelineLock`
3. `PipelineRecorder` 인터페이스 + DB 구현체
4. `ExecutionTracker` (중복 실행 방지)
5. `PipelineDataValidator` (단계별 검증)
6. `PipelineOptions` record

**검증:**
- Mock 구현체로 전체 파이프라인 통합 테스트
- 잠금/중복 방지 테스트

---

### 6단계: 관리자 대시보드

**목표:** Express 대시보드를 Spring MVC로 전환

**작업 내용:**
1. JPA 엔티티: `PipelineRunEntity`, `PipelineLogEntity`, `PipelineItemEntity`
2. JPA Repository 3개
3. `StatusController`, `RunController`, `ItemController`
4. 응답 DTO
5. `AdminTokenFilter` (Bearer 토큰 인증)
6. `SecurityConfig` (Spring Security 설정)
7. `GlobalExceptionHandler`

**검증:**
- 컨트롤러 단위 테스트 (`@WebMvcTest`)
- 인증 필터 테스트
- Repository 테스트 (`@DataJpaTest`)

---

### 7단계: 스케줄러 & CLI

**목표:** 매시간 자동 실행 + 커맨드라인 수동 실행

**작업 내용:**
1. `BriefingScheduler` (`@Scheduled`)
2. `SchedulingConfig`
3. `CommandLineRunner` 구현 (CLI 모드)
   - `--target-date`, `--dry-run` 인자 처리
   - 실행 후 JSON 결과 출력, `System.exit()`
4. Profile로 스케줄러 ON/OFF 제어

**검증:**
- 스케줄러 동작 테스트 (`@SpringBootTest`)
- CLI 모드 테스트

---

### 8단계: GitHub Actions

**목표:** 기존 워크플로우를 Gradle + Java로 전환

**작업 내용:**

`weekly-briefing.yml` 수정:
```yaml
steps:
  - uses: actions/checkout@v4
  - uses: actions/setup-java@v4
    with:
      distribution: 'temurin'
      java-version: '21'
  - name: Build & Test
    run: ./gradlew build
  - name: Run Briefing
    run: java -jar build/libs/economic-briefing-0.1.0.jar --target-date=${{ ... }}
    env:
      SPRING_PROFILES_ACTIVE: prod
      OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
      NOTION_API_KEY: ${{ secrets.NOTION_API_KEY }}
      NOTION_DATABASE_ID: ${{ secrets.NOTION_DATABASE_ID }}
```

`briefing-watchdog.yml` 수정: 동일 구조, 빌드 명령만 변경

**검증:**
- GitHub Actions 수동 트리거 테스트
- Watchdog 동작 확인

---

### 9단계: 테스트 & 최종 검증

**목표:** 전체 기능 검증, Node.js 프로젝트와 동일한 결과 확인

**작업 내용:**
1. 단위 테스트 전체 실행 확인
2. 통합 테스트 (H2 DB, Mock API)
3. 실제 환경 E2E 테스트:
   - DRY_RUN=true: Mock으로 전체 파이프라인 실행
   - DRY_RUN=false: 실제 RSS + OpenAI + Notion 테스트
4. Node.js 결과와 Java 결과 비교
5. GitHub Actions 전체 워크플로우 실행

**최종 검증 체크리스트:**
- [ ] 10개 RSS 소스 수집 정상
- [ ] 다단계 필터링 (날짜/품질/중복/카테고리/관련성/다양성) 정상
- [ ] OpenAI API 호출 + 재시도 정상
- [ ] Briefing 생성 정상
- [ ] Notion 발행 + 중복 방지 정상
- [ ] 관리자 API 전체 엔드포인트 정상
- [ ] 스케줄러 매시간 실행 정상
- [ ] CLI 수동 실행 정상
- [ ] GitHub Actions 자동 실행 정상
- [ ] Watchdog 워크플로우 정상
- [ ] 에러 처리 + 로그 정상
- [ ] KST 시간대 처리 정상

---

## 기술 결정 요약

| 항목 | Node.js (현재) | Spring Boot (전환 후) |
|------|---------------|---------------------|
| 언어 | TypeScript | Java 21 |
| 런타임 | Node.js 20 | JVM (Spring Boot 3.4) |
| 빌드 | npm/tsc | Gradle (Kotlin DSL) |
| HTTP 클라이언트 | fetch (내장) | WebClient (Spring WebFlux) |
| 검증 | Zod | Jakarta Validation + Jackson |
| DI | 수동 조합 (createApplication) | Spring IoC Container |
| 스케줄링 | GitHub Actions cron | Spring @Scheduled + GitHub Actions |
| DB ORM | 직접 SQL (pg) | Spring Data JPA + Hibernate |
| DB 마이그레이션 | 수동 migrate.ts | Flyway |
| 웹 프레임워크 | Express | Spring MVC |
| 인증 | 미들웨어 (Bearer) | Spring Security Filter |
| 테스트 | Vitest | JUnit 5 + Mockito |
| HTTP 테스트 | supertest | MockMvc |
| RSS 파싱 | rss-parser (npm) | Rome (Java) |
| 로깅 | console.log | SLF4J + Logback |
| 에러 타입 | AppError class | BriefingException 계층 |
| 환경 설정 | .env + Zod | application.yml + @ConfigurationProperties |
| Mock 전환 | DRY_RUN 플래그 | @ConditionalOnProperty + Spring Profile |
| 비동기 | Promise.allSettled | CompletableFuture + @Async |
| Result 타입 | Result<T> 유틸 | 예외 기반 흐름 (Java 관례) |

---

## 주의사항

1. **프롬프트 이식**: `systemPrompt.ts`의 129줄 한국어 프롬프트를 그대로 Java String으로 이식. 내용 변경 금지.
2. **RSS URL**: 10개 소스의 RSS URL을 그대로 유지. 변경 시 수집이 깨짐.
3. **카테고리 키워드**: `categoryClassifier.ts`의 한국어 키워드 맵을 그대로 이식.
4. **관련성 점수 키워드**: `relevanceScorer.ts`의 키워드-점수 맵을 그대로 이식.
5. **Notion 블록 구조**: `buildNotionPage.ts`의 블록 구성을 그대로 유지. Notion API 호환성 필수.
6. **시간대**: 모든 날짜/시간 처리는 KST(Asia/Seoul) 기준. `java.time` API 사용.
7. **GitHub Actions**: Node.js 의존성 제거, Java/Gradle 빌드로 전환.
8. **DB 스키마**: 기존 테이블 구조 유지. Flyway로 마이그레이션 관리.
