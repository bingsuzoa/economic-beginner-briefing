package com.economicbriefing.analyzer.openai.util;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.economicbriefing.analyzer.openai.dto.AiResponse;
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
                10,
                "gpt-4o",
                "v3",
                null,
                null
        );

        assertEquals("briefing-2025-01-15", briefing.id());
        assertEquals(LocalDate.of(2025, 1, 15), briefing.targetDate());
        assertEquals("2025-01-15 경제 브리핑", briefing.title());
        assertNotNull(briefing.generatedAt());
        assertEquals(1, briefing.news().size());

        // sources는 자동 매핑됨
        var news = briefing.news().get(0);
        assertEquals(1, news.sources().size());
        assertEquals("article-1", news.sources().get(0).articleId());
        assertEquals("한국은행", news.sources().get(0).sourceName());
        assertTrue(news.sources().get(0).isPrimary());
    }

    @Test
    void shouldMapGlossary() {
        AiResponse aiResponse = new AiResponse(
                List.of("요약"),
                List.of(new AiResponse.AiAnalyzedNews(
                        "news-1", "제목", "interest_rate", 5,
                        List.of("핵심"), "사건", "원인", "초보자 설명", "경제영향",
                        List.of(), "confirmed"
                )),
                List.of(new AiResponse.AiEconomicTerm("기준금리", "설명", "예시"))
        );

        Briefing briefing = BriefingBuilder.build(
                aiResponse, LocalDate.of(2025, 1, 15),
                createArticles(), 10, "gpt-4o", "v3", null, null);

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
                articles, 100, "gpt-4o", "v3", null, null);

        assertEquals(100, briefing.metadata().collectedArticleCount());
        assertEquals(1, briefing.metadata().selectedNewsCount());
        assertEquals("gpt-4o", briefing.metadata().modelName());
        assertEquals("v3", briefing.metadata().promptVersion());
    }

    @Test
    void shouldHandleUnknownCategory() {
        AiResponse aiResponse = new AiResponse(
                List.of("요약"),
                List.of(new AiResponse.AiAnalyzedNews(
                        "news-1", "제목", "unknown_category", 3,
                        List.of("핵심"), "사건", "원인", "초보자 설명", "경제영향",
                        List.of(), "confirmed"
                )),
                List.of()
        );

        Briefing briefing = BriefingBuilder.build(
                aiResponse, LocalDate.of(2025, 1, 15),
                createArticles(), 10, "gpt-4o", "v3", null, null);

        assertEquals(NewsCategory.OTHER, briefing.news().get(0).category());
    }

    @Test
    void shouldHandleEmptyOverallSummary() {
        AiResponse aiResponse = new AiResponse(
                List.of(),
                List.of(new AiResponse.AiAnalyzedNews(
                        "news-1", "제목", "interest_rate", 3,
                        List.of("핵심"), "사건", "원인", "초보자 설명", "경제영향",
                        List.of(), "confirmed"
                )),
                List.of()
        );

        Briefing briefing = BriefingBuilder.build(
                aiResponse, LocalDate.of(2025, 1, 15),
                createArticles(), 10, "gpt-4o", "v3", null, null);

        assertNotNull(briefing.overallSummary());
        assertTrue(briefing.overallSummary().isEmpty());
    }

    @Test
    void shouldHandleNullOverallSummary() {
        AiResponse aiResponse = new AiResponse(
                null,
                List.of(new AiResponse.AiAnalyzedNews(
                        "news-1", "제목", "interest_rate", 3,
                        List.of("핵심"), "사건", "원인", "초보자 설명", "경제영향",
                        List.of(), "confirmed"
                )),
                List.of()
        );

        Briefing briefing = BriefingBuilder.build(
                aiResponse, LocalDate.of(2025, 1, 15),
                createArticles(), 10, "gpt-4o", "v3", null, null);

        assertNotNull(briefing.overallSummary());
        assertTrue(briefing.overallSummary().isEmpty());
    }

    @Test
    void shouldUseCustomBriefingTitle() {
        AiResponse aiResponse = createAiResponse();
        List<Article> articles = createArticles();

        Briefing briefing = BriefingBuilder.build(
                aiResponse, LocalDate.of(2025, 1, 15),
                articles, 10, "gpt-4o", "v3", "커스텀 제목", null);

        assertEquals("커스텀 제목", briefing.title());
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
                        "경기가 나빠질 것 같아서 금리를 낮췄어요",
                        "시중금리 하락",
                        List.of(new AiResponse.AiEconomicTerm("기준금리", "설명", null)),
                        "confirmed"
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
