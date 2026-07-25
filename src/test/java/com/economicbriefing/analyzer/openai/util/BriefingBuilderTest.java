package com.economicbriefing.analyzer.openai.util;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.economicbriefing.analyzer.openai.dto.AiResponse;
import com.economicbriefing.domain.analysis.NewsEvidenceStatus;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import com.economicbriefing.domain.briefing.Briefing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BriefingBuilderTest {

    @Test
    void shouldBuildBriefingFromAiResponse() {
        AiResponse aiResponse = createAiResponse();
        List<Article> articles = createArticles();

        Briefing briefing = BriefingBuilder.build(
                aiResponse,
                LocalDate.of(2025, 1, 15),
                articles,
                "gpt-4o",
                "v2",
                null,
                null
        );

        assertEquals("briefing-2025-01-15", briefing.id());
        assertEquals(LocalDate.of(2025, 1, 15), briefing.targetDate());
        assertEquals("2025-01-15 경제 브리핑", briefing.title());
        assertNotNull(briefing.generatedAt());
    }

    @Test
    void shouldMapNewsWithNewFields() {
        AiResponse aiResponse = createAiResponse();
        List<Article> articles = createArticles();

        Briefing briefing = BriefingBuilder.build(
                aiResponse, LocalDate.of(2025, 1, 15),
                articles, "gpt-4o", "v2", null, null);

        assertEquals(1, briefing.news().size());
        var news = briefing.news().get(0);
        assertEquals("news-1", news.id());
        assertEquals("기준금리가 내려갔어요", news.easyTitle());
        assertEquals(NewsCategory.INTEREST_RATE, news.category());
        assertEquals(5, news.importance());
        assertEquals(3, news.threeLineSummary().size());
        assertNotNull(news.whatHappened());
        assertNotNull(news.whyItHappened());
        assertEquals(NewsEvidenceStatus.CONFIRMED, news.evidenceStatus());

        assertEquals(1, news.sources().size());
        assertEquals("한국은행", news.sources().get(0).sourceName());
        assertEquals("기준금리 인하", news.sources().get(0).title());
    }

    @Test
    void shouldHandleMissingArticleInMap() {
        AiResponse aiResponse = new AiResponse(
                List.of("요약"),
                List.of(new AiResponse.AiAnalyzedNews(
                        "news-1", "제목", "interest_rate", 5,
                        List.of("핵심"), "사건", "원인", "경제영향", "생활영향",
                        List.of(), "긍정", "부정", List.of(),
                        List.of(), "confirmed", List.of(),
                        List.of(new AiResponse.AiSourceReference("non-existent-id", true))
                )),
                List.of()
        );

        Briefing briefing = BriefingBuilder.build(
                aiResponse, LocalDate.of(2025, 1, 15),
                List.of(), "gpt-4o", "v2", null, null);

        assertEquals("Unknown", briefing.news().get(0).sources().get(0).sourceName());
    }

    @Test
    void shouldUseCustomBriefingTitle() {
        AiResponse aiResponse = createAiResponse();
        List<Article> articles = createArticles();

        Briefing briefing = BriefingBuilder.build(
                aiResponse, LocalDate.of(2025, 1, 15),
                articles, "gpt-4o", "v2", "커스텀 제목", null);

        assertEquals("커스텀 제목", briefing.title());
    }

    @Test
    void shouldMapGlossary() {
        AiResponse aiResponse = new AiResponse(
                List.of("요약"),
                List.of(new AiResponse.AiAnalyzedNews(
                        "news-1", "제목", "interest_rate", 5,
                        List.of("핵심"), "사건", "원인", "경제영향", "생활영향",
                        List.of(), "긍정", "부정", List.of(),
                        List.of(), "confirmed", List.of(),
                        List.of(new AiResponse.AiSourceReference("article-1", true))
                )),
                List.of(new AiResponse.AiEconomicTerm("기준금리", "설명", "예시"))
        );

        Briefing briefing = BriefingBuilder.build(
                aiResponse, LocalDate.of(2025, 1, 15),
                createArticles(), "gpt-4o", "v2", null, null);

        assertEquals(1, briefing.glossary().size());
        assertEquals("기준금리", briefing.glossary().get(0).term());
        assertEquals("예시", briefing.glossary().get(0).example());
    }

    @Test
    void shouldSetMetadata() {
        AiResponse aiResponse = createAiResponse();
        List<Article> articles = createArticles();

        Briefing briefing = BriefingBuilder.build(
                aiResponse, LocalDate.of(2025, 1, 15),
                articles, "gpt-4o", "v2", null, null);

        assertEquals(1, briefing.metadata().collectedArticleCount());
        assertEquals(1, briefing.metadata().selectedNewsCount());
        assertEquals("gpt-4o", briefing.metadata().modelName());
        assertEquals("v2", briefing.metadata().promptVersion());
    }

    @Test
    void shouldHandleUnknownCategory() {
        AiResponse aiResponse = new AiResponse(
                List.of("요약"),
                List.of(new AiResponse.AiAnalyzedNews(
                        "news-1", "제목", "unknown_category", 3,
                        List.of("핵심"), "사건", "원인", "경제영향", "생활영향",
                        List.of(), "긍정", "부정", List.of(),
                        List.of(), "confirmed", List.of(),
                        List.of(new AiResponse.AiSourceReference("article-1", true))
                )),
                List.of()
        );

        Briefing briefing = BriefingBuilder.build(
                aiResponse, LocalDate.of(2025, 1, 15),
                createArticles(), "gpt-4o", "v2", null, null);

        assertEquals(NewsCategory.OTHER, briefing.news().get(0).category());
    }

    private AiResponse createAiResponse() {
        return new AiResponse(
                List.of("전체 요약 문장"),
                List.of(new AiResponse.AiAnalyzedNews(
                        "news-1",
                        "기준금리가 내려갔어요",
                        "interest_rate",
                        5,
                        List.of("핵심 1", "핵심 2", "핵심 3"),
                        "기준금리가 인하되었습니다.",
                        "경기 둔화 우려",
                        "시중금리 하락",
                        "대출 이자 감소",
                        List.of("변동금리 대출자"),
                        "대출 이자 부담 감소",
                        "예금 이자 수입 감소",
                        List.of(),
                        List.of(new AiResponse.AiEconomicTerm("기준금리", "설명", null)),
                        "confirmed",
                        List.of(),
                        List.of(new AiResponse.AiSourceReference("article-1", true))
                )),
                List.of()
        );
    }

    private List<Article> createArticles() {
        OffsetDateTime now = LocalDate.of(2025, 1, 15)
                .atTime(10, 0).atOffset(ZoneOffset.ofHours(9));
        return List.of(new Article(
                "article-1", "기준금리 인하", "기준금리가 인하되었습니다.",
                "한국은행", ArticleSourceType.GOVERNMENT,
                now, now, "https://example.com/1",
                List.of(NewsCategory.INTEREST_RATE), "ko", null
        ));
    }
}
