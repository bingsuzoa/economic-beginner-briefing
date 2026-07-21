# Architecture

## 1. 설계 원칙

이 프로젝트는 기능을 다음 세 영역으로 분리한다.

```text
Collect
→ Analyze
→ Publish
```

각 영역은 다른 영역의 구체적인 구현을 몰라야 한다.

예를 들어 뉴스 수집기는 OpenAI API나 Notion API의 존재를 알 필요가 없다.

## 2. 디렉터리 구조

```text
src/
├─ main/java/com/economicbriefing/
│  ├─ EconomicBriefingApplication.java
│  ├─ collector/          # 뉴스 수집 (RSS)
│  ├─ analyzer/           # AI 분석 (OpenAI)
│  ├─ publisher/          # 결과 발행 (Notion)
│  ├─ pipeline/           # 파이프라인 오케스트레이션
│  ├─ domain/             # 도메인 모델
│  ├─ config/             # Spring 설정
│  └─ common/             # 공통 유틸
├─ main/resources/
│  ├─ application.yml
│  ├─ application-prod.yml
│  └─ db/migration/       # Flyway SQL
└─ test/java/com/economicbriefing/

docs/
build.gradle.kts
settings.gradle.kts
gradlew
```

Collect·Analyze·Publish의 책임 분리는 유지해야 한다.

## 3. 주요 인터페이스

### NewsCollector

```java
public interface NewsCollector {
    CollectNewsResult collect(CollectNewsRequest request);
}
```

책임:

- 지정 기간에 해당하는 기사 수집
- 기사 데이터 정규화
- 수집 단계 오류 보고

책임이 아닌 것:

- 뉴스 중요도 판단
- 초보자용 설명 생성
- 이메일 및 Notion 저장

### NewsAnalyzer

```java
public interface NewsAnalyzer {
    AnalyzeNewsResult analyze(AnalyzeNewsRequest request);
}
```

책임:

- 기사 중요도 평가
- 같은 사건 그룹화
- 대표 출처 선정
- 최종 뉴스 선별
- 경제 초보자용 설명 생성
- 사실과 예상 구분

책임이 아닌 것:

- 뉴스 사이트 접근
- Notion 저장
- 이메일 발송

### BriefingPublisher

```java
public interface BriefingPublisher {
    PublishBriefingResult publish(PublishBriefingRequest request);
}
```

책임:

- 완성된 브리핑을 외부 채널로 전달
- 전달 성공 및 실패 결과 반환

구현체:

- `NotionBriefingPublisher`

## 4. 애플리케이션 서비스

전체 순서를 조율하는 코드는 `BriefingPipeline`에 둔다.

```java
CollectNewsResult collection = collector.collect(collectRequest);
AnalyzeNewsResult analysis = analyzer.analyze(
    new AnalyzeNewsRequest(collection.getArticles(), targetDate));
PublishBriefingResult publication = publisher.publish(
    new PublishBriefingRequest(analysis.getBriefing()));
```

애플리케이션 서비스는 세부 구현보다 실행 순서, 상태, 오류 정책을 관리한다.

## 5. 의존성 방향

```text
pipeline (orchestration)
↓
interfaces / domain
↑
collector, analyzer, publisher implementations
```

도메인 타입은 외부 SDK 타입에 의존하면 안 된다.

잘못된 예:

```java
public class Article {
    private NotionPage notionPage; // 외부 SDK 타입 사용 금지
}
```

올바른 예:

```java
public class Article {
    private String id;
    private String title;
    private String url;
}
```

외부 SDK 타입은 각 어댑터 내부에서만 사용한다.

## 6. 설정

환경변수는 Spring Boot `application.yml`과 `@ConfigurationProperties`로 관리한다.

주요 환경변수:

```env
SPRING_PROFILES_ACTIVE=default
TZ=Asia/Seoul
DRY_RUN=true
LOG_LEVEL=info

OPENAI_API_KEY=
NOTION_API_KEY=
NOTION_DATABASE_ID=
```

테스트에서는 H2 인메모리 DB를 사용하며, 외부 API 관련 값이 없어도 실행 가능해야 한다.

## 7. 확장 방향

향후 다음 기능을 추가할 수 있어야 한다.

- 새로운 RSS 수집처
- 정부기관 보도자료 수집기
- 다른 AI 분석기
- 이메일 제공업체 변경
- 주간 요약
- 월간 경제 흐름 분석
- 사용자별 관심 카테고리
- PostgreSQL 실행 이력 저장
