package com.economicbriefing.pipeline;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.economicbriefing.analyzer.dto.AnalyzeNewsResult;
import com.economicbriefing.collector.dto.CollectNewsResult;
import com.economicbriefing.domain.analysis.AnalyzedNews;
import com.economicbriefing.domain.analysis.EconomicTerm;
import com.economicbriefing.domain.analysis.NewsEvidenceStatus;
import com.economicbriefing.domain.analysis.SourceReference;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import com.economicbriefing.domain.briefing.Briefing;
import com.economicbriefing.domain.briefing.BriefingMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PipelineDataValidatorTest {

    private PipelineDataValidator validator;
    private final LocalDate targetDate = LocalDate.of(2025, 1, 15);

    @BeforeEach
    void setUp() {
        validator = new PipelineDataValidator();
    }

    @Test
    void shouldValidateCollectResultWithValidArticles() {
        List<Article> articles = List.of(createArticle("a-1", "제목", "https://example.com/1"));
        CollectNewsResult result = new CollectNewsResult(targetDate, articles, List.of(), 1, 1, 0);

        var validation = validator.validateCollectResult(result, targetDate);

        assertEquals(1, validation.validArticles().size());
        assertTrue(validation.warnings().isEmpty());
    }

    @Test
    void shouldWarnOnDateMismatch() {
        List<Article> articles = List.of(createArticle("a-1", "제목", "https://example.com/1"));
        CollectNewsResult result = new CollectNewsResult(
                LocalDate.of(2025, 1, 16), articles, List.of(), 1, 1, 0);

        var validation = validator.validateCollectResult(result, targetDate);

        assertEquals(1, validation.warnings().size());
        assertTrue(validation.warnings().get(0).message().contains("targetDate mismatch"));
    }

    @Test
    void shouldExcludeArticlesWithMissingFields() {
        List<Article> articles = List.of(
                createArticle("a-1", "제목", "https://example.com/1"),
                createArticle(null, "제목없는", "https://example.com/2"),
                createArticle("a-3", "", "https://example.com/3")
        );
        CollectNewsResult result = new CollectNewsResult(targetDate, articles, List.of(), 3, 1, 2);

        var validation = validator.validateCollectResult(result, targetDate);

        assertEquals(1, validation.validArticles().size());
        assertEquals(2, validation.warnings().size());
    }

    @Test
    void shouldValidateAnalyzeResultWithValidNews() {
        AnalyzeNewsResult result = createAnalyzeResult(List.of(
                createAnalyzedNews("news-1", List.of(createSource("https://example.com")))
        ));

        var validation = validator.validateAnalyzeResult(result, targetDate);

        assertTrue(validation.valid());
        assertTrue(validation.warnings().isEmpty());
    }

    @Test
    void shouldRejectAnalyzeResultWithEmptyNews() {
        AnalyzeNewsResult result = createAnalyzeResult(List.of());

        var validation = validator.validateAnalyzeResult(result, targetDate);

        assertFalse(validation.valid());
    }

    @Test
    void shouldExcludeNewsWithNoSources() {
        AnalyzeNewsResult result = createAnalyzeResult(List.of(
                createAnalyzedNews("news-1", List.of()),
                createAnalyzedNews("news-2", List.of(createSource("https://example.com")))
        ));

        var validation = validator.validateAnalyzeResult(result, targetDate);

        assertTrue(validation.valid());
        assertEquals(1, validation.warnings().size());
        assertTrue(validation.warnings().get(0).message().contains("no source references"));
    }

    @Test
    void shouldExcludeNewsWithNoSourceUrls() {
        SourceReference noUrlSource = new SourceReference("a-1", "출처", "제목", null,
                OffsetDateTime.now(ZoneOffset.ofHours(9)), true);
        AnalyzeNewsResult result = createAnalyzeResult(List.of(
                createAnalyzedNews("news-1", List.of(noUrlSource))
        ));

        var validation = validator.validateAnalyzeResult(result, targetDate);

        assertFalse(validation.valid());
        assertTrue(validation.warnings().stream()
                .anyMatch(w -> w.message().contains("no source URLs")));
    }

    private Article createArticle(String id, String title, String url) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(9));
        return new Article(id, title, "요약", "출처", ArticleSourceType.NEWS_MEDIA,
                now, now, url, List.of(NewsCategory.OTHER), "ko", null);
    }

    private SourceReference createSource(String url) {
        return new SourceReference("a-1", "출처", "제목", url,
                OffsetDateTime.now(ZoneOffset.ofHours(9)), true);
    }

    private AnalyzedNews createAnalyzedNews(String id, List<SourceReference> sources) {
        return new AnalyzedNews(id, "제목", NewsCategory.OTHER, 3,
                "이유", null, null, "결론", "해설",
                NewsEvidenceStatus.CONFIRMED, null, List.of(), sources);
    }

    private AnalyzeNewsResult createAnalyzeResult(List<AnalyzedNews> news) {
        Briefing briefing = new Briefing(
                "briefing-" + targetDate, targetDate,
                OffsetDateTime.now(ZoneOffset.ofHours(9)),
                targetDate + " 경제 브리핑",
                List.of("요약"), news, List.of(),
                new BriefingMetadata(5, 5, news.size(), "mock", "v1")
        );
        return new AnalyzeNewsResult(briefing, List.of(), List.of());
    }
}
