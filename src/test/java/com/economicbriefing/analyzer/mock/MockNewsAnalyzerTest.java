package com.economicbriefing.analyzer.mock;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.economicbriefing.analyzer.dto.AnalyzeNewsRequest;
import com.economicbriefing.analyzer.dto.AnalyzeNewsResult;
import com.economicbriefing.domain.analysis.AudienceProfile;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockNewsAnalyzerTest {

    private MockNewsAnalyzer analyzer;
    private AudienceProfile audience;

    @BeforeEach
    void setUp() {
        analyzer = new MockNewsAnalyzer();
        audience = new AudienceProfile(
                "beginner",
                List.of(NewsCategory.INTEREST_RATE, NewsCategory.HOUSING),
                List.of("신혼부부")
        );
    }

    @Test
    void shouldAnalyzeMockArticles() {
        List<Article> articles = createMockArticles();
        AnalyzeNewsRequest request = new AnalyzeNewsRequest(
                articles, LocalDate.of(2025, 1, 15), 10, audience, null);

        AnalyzeNewsResult result = analyzer.analyze(request);

        assertNotNull(result.briefing());
        assertEquals(3, result.briefing().news().size());
        assertEquals(0, result.rejectedArticleIds().size());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void shouldLimitToMaxSelectedNews() {
        List<Article> articles = createMockArticles();
        AnalyzeNewsRequest request = new AnalyzeNewsRequest(
                articles, LocalDate.of(2025, 1, 15), 2, audience, null);

        AnalyzeNewsResult result = analyzer.analyze(request);

        assertEquals(2, result.briefing().news().size());
        assertEquals(1, result.rejectedArticleIds().size());
    }

    @Test
    void shouldUseMockAnalysisDetailsForKnownArticles() {
        List<Article> articles = createMockArticles();
        AnalyzeNewsRequest request = new AnalyzeNewsRequest(
                articles, LocalDate.of(2025, 1, 15), 10, audience, null);

        AnalyzeNewsResult result = analyzer.analyze(request);

        var firstNews = result.briefing().news().get(0);
        assertEquals("analyzed-mock-article-001", firstNews.id());
        assertTrue(firstNews.explanation().contains("[무슨 일이 있었나]"));
        assertTrue(firstNews.explanation().contains("[왜 이런 일이 발생했나]"));
        assertTrue(firstNews.explanation().contains("[우리에게 어떤 의미가 있나]"));
        assertFalse(firstNews.economicTerms().isEmpty());
        assertEquals("기준금리", firstNews.economicTerms().get(0).term());
    }

    @Test
    void shouldIncludeOverallSummary() {
        List<Article> articles = createMockArticles();
        AnalyzeNewsRequest request = new AnalyzeNewsRequest(
                articles, LocalDate.of(2025, 1, 15), 10, audience, null);

        AnalyzeNewsResult result = analyzer.analyze(request);

        assertEquals(2, result.briefing().overallSummary().size());
        assertTrue(result.briefing().overallSummary().get(0).contains("기준금리"));
    }

    @Test
    void shouldBuildGlossaryFromUniqueTerms() {
        List<Article> articles = createMockArticles();
        AnalyzeNewsRequest request = new AnalyzeNewsRequest(
                articles, LocalDate.of(2025, 1, 15), 10, audience, null);

        AnalyzeNewsResult result = analyzer.analyze(request);

        assertFalse(result.briefing().glossary().isEmpty());
        long distinctTerms = result.briefing().glossary().stream()
                .map(t -> t.term())
                .distinct()
                .count();
        assertEquals(result.briefing().glossary().size(), distinctTerms);
    }

    @Test
    void shouldSetBriefingMetadata() {
        List<Article> articles = createMockArticles();
        AnalyzeNewsRequest request = new AnalyzeNewsRequest(
                articles, LocalDate.of(2025, 1, 15), 10, audience, null);

        AnalyzeNewsResult result = analyzer.analyze(request);

        assertEquals("mock", result.briefing().metadata().modelName());
        assertEquals("foundation-v1", result.briefing().metadata().promptVersion());
        assertEquals(3, result.briefing().metadata().collectedArticleCount());
        assertEquals(3, result.briefing().metadata().selectedNewsCount());
    }

    @Test
    void shouldUseCustomBriefingTitle() {
        List<Article> articles = createMockArticles();
        AnalyzeNewsRequest request = new AnalyzeNewsRequest(
                articles, LocalDate.of(2025, 1, 15), 10, audience, "커스텀 제목");

        AnalyzeNewsResult result = analyzer.analyze(request);

        assertEquals("커스텀 제목", result.briefing().title());
    }

    @Test
    void shouldHandleUnknownArticleIds() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(9));
        Article unknownArticle = new Article(
                "unknown-id", "테스트 기사", "테스트 요약",
                "테스트", ArticleSourceType.NEWS_MEDIA,
                now, now, "https://example.com/test",
                List.of(NewsCategory.OTHER), "ko", null
        );
        AnalyzeNewsRequest request = new AnalyzeNewsRequest(
                List.of(unknownArticle), LocalDate.of(2025, 1, 15), 10, audience, null);

        AnalyzeNewsResult result = analyzer.analyze(request);

        assertEquals(1, result.briefing().news().size());
        assertEquals("일반 가계에 직접적인 영향이 있는 경제 뉴스입니다.",
                result.briefing().news().get(0).whyImportant());
    }

    private List<Article> createMockArticles() {
        OffsetDateTime publishedAt = LocalDate.of(2025, 1, 15)
                .atTime(10, 0).atOffset(ZoneOffset.ofHours(9));
        OffsetDateTime collectedAt = LocalDate.of(2025, 1, 15)
                .atTime(23, 30).atOffset(ZoneOffset.ofHours(9));

        return List.of(
                new Article("mock-article-001", "한국은행, 기준금리 0.25%p 인하 결정",
                        "한국은행 금융통화위원회가 기준금리를 연 3.25%에서 3.00%로 0.25%포인트 인하했다.",
                        "한국은행", ArticleSourceType.GOVERNMENT,
                        publishedAt, collectedAt,
                        "https://example.com/articles/base-rate-cut",
                        List.of(NewsCategory.INTEREST_RATE), "ko", null),
                new Article("mock-article-002", "전세보증금 별도관리 제도 검토 착수",
                        "금융위원회가 전세보증금을 집주인이 직접 보유하지 않고 별도 기관에서 관리하는 방안을 검토하기로 했다.",
                        "금융위원회", ArticleSourceType.GOVERNMENT,
                        publishedAt, collectedAt,
                        "https://example.com/articles/jeonse-deposit-management",
                        List.of(NewsCategory.JEONSE_MONTHLY_RENT, NewsCategory.HOUSING), "ko", null),
                new Article("mock-article-003", "주요 은행, 정기예금 금리 일제히 인하",
                        "기준금리 인하 영향으로 KB국민, 신한, 우리 등 주요 시중은행이 정기예금 금리를 0.1~0.2%p 내렸다.",
                        "연합뉴스", ArticleSourceType.NEWS_MEDIA,
                        publishedAt, collectedAt,
                        "https://example.com/articles/deposit-rate-drop",
                        List.of(NewsCategory.DEPOSIT_SAVING, NewsCategory.INTEREST_RATE), "ko", null)
        );
    }
}
