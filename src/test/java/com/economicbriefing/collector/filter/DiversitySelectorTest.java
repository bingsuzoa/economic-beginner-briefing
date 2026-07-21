package com.economicbriefing.collector.filter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.economicbriefing.config.AppProperties;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiversitySelectorTest {

    private DiversitySelector selector;
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @BeforeEach
    void setUp() {
        AppProperties.DiversityProperties diversity = new AppProperties.DiversityProperties(3, 3, 3, 5);
        AppProperties props = new AppProperties(
                true,
                new AppProperties.TimeoutProperties(null, null, null, null),
                new AppProperties.RetryProperties(2, null, null),
                diversity,
                new AppProperties.AudienceProperties("beginner", List.of(), List.of()),
                new AppProperties.SchedulerProperties(false, "0 0 * * * *")
        );
        selector = new DiversitySelector(props);
    }

    @Test
    void shouldSelectArticlesAboveMinRelevance() {
        List<Article> articles = List.of(
                createArticle("1", "연합뉴스", NewsCategory.INTEREST_RATE),
                createArticle("2", "한국경제", NewsCategory.HOUSING)
        );
        List<RelevanceScorer.RelevanceScore> scores = List.of(
                new RelevanceScorer.RelevanceScore("1", 5, List.of()),
                new RelevanceScorer.RelevanceScore("2", 4, List.of())
        );

        DiversitySelector.DiversitySelectionResult result = selector.select(
                articles, scores, DiversitySelector.DiversityOptions.defaults());

        assertEquals(2, result.selected().size());
    }

    @Test
    void shouldExcludeLowRelevanceArticles() {
        List<Article> articles = List.of(
                createArticle("1", "연합뉴스", NewsCategory.INTEREST_RATE),
                createArticle("2", "한국경제", NewsCategory.OTHER)
        );
        List<RelevanceScorer.RelevanceScore> scores = List.of(
                new RelevanceScorer.RelevanceScore("1", 5, List.of()),
                new RelevanceScorer.RelevanceScore("2", 1, List.of())
        );

        DiversitySelector.DiversitySelectionResult result = selector.select(
                articles, scores, DiversitySelector.DiversityOptions.defaults());

        assertEquals(1, result.selected().size());
        assertEquals(1, result.stats().excludedByRelevance());
    }

    @Test
    void shouldEnforceSourceLimit() {
        List<Article> articles = new ArrayList<>();
        List<RelevanceScorer.RelevanceScore> scores = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            articles.add(createArticle("id-" + i, "같은소스", NewsCategory.values()[i % 5]));
            scores.add(new RelevanceScorer.RelevanceScore("id-" + i, 5, List.of()));
        }

        DiversitySelector.DiversitySelectionResult result = selector.select(
                articles, scores, DiversitySelector.DiversityOptions.defaults());

        assertEquals(3, result.selected().size());
        assertTrue(result.stats().excludedBySourceLimit() > 0);
    }

    @Test
    void shouldEnforceCategorySoftCap() {
        List<Article> articles = new ArrayList<>();
        List<RelevanceScorer.RelevanceScore> scores = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            articles.add(createArticle("id-" + i, "소스" + i, NewsCategory.INTEREST_RATE));
            scores.add(new RelevanceScorer.RelevanceScore("id-" + i, 4, List.of()));
        }

        DiversitySelector.DiversitySelectionResult result = selector.select(
                articles, scores, DiversitySelector.DiversityOptions.defaults());

        assertEquals(3, result.selected().size());
    }

    @Test
    void shouldAllowSoftCapOverrideForHighScore() {
        List<Article> articles = new ArrayList<>();
        List<RelevanceScorer.RelevanceScore> scores = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            articles.add(createArticle("id-" + i, "소스" + i, NewsCategory.INTEREST_RATE));
            scores.add(new RelevanceScorer.RelevanceScore("id-" + i, 5, List.of()));
        }

        DiversitySelector.DiversitySelectionResult result = selector.select(
                articles, scores, DiversitySelector.DiversityOptions.defaults());

        // soft cap 3 + 1 override = 4
        assertEquals(4, result.selected().size());
    }

    private Article createArticle(String id, String source, NewsCategory category) {
        return new Article(id, "테스트 제목 " + id, "요약", source,
                ArticleSourceType.NEWS_MEDIA,
                OffsetDateTime.of(2026, 7, 20, 12, 0, 0, 0, KST),
                OffsetDateTime.now(KST),
                "https://example.com/" + id,
                List.of(category), "ko", null);
    }
}
