package com.economicbriefing.collector.filter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateRemoverTest {

    private final DuplicateRemover remover = new DuplicateRemover();
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @Test
    void shouldRemoveDuplicateById() {
        Article a = createArticle("dup-id", "연합뉴스", "기준금리 인하 결정", "https://example.com/1");
        Article b = createArticle("dup-id", "한국경제", "기준금리 인하 결정", "https://example.com/2");

        DuplicateRemover.DeduplicationResult result = remover.removeDuplicates(List.of(a, b));

        assertEquals(1, result.unique().size());
        assertEquals(1, result.duplicates().size());
    }

    @Test
    void shouldRemoveDuplicateByUrl() {
        Article a = createArticle("id-1", "연합뉴스", "기준금리 인하", "https://example.com/same");
        Article b = createArticle("id-2", "한국경제", "기준금리 인하", "https://example.com/same");

        DuplicateRemover.DeduplicationResult result = remover.removeDuplicates(List.of(a, b));

        assertEquals(1, result.unique().size());
    }

    @Test
    void shouldNormalizeUrlTrackingParams() {
        String normalized = DuplicateRemover.normalizeUrl(
                "https://example.com/article?utm_source=google&id=123&utm_medium=cpc");
        assertFalse(normalized.contains("utm_source"));
        assertFalse(normalized.contains("utm_medium"));
        assertTrue(normalized.contains("id=123"));
    }

    @Test
    void shouldNormalizeMobileUrl() {
        String normalized = DuplicateRemover.normalizeUrl("https://m.example.com/article");
        assertTrue(normalized.contains("www.example.com"));
    }

    @Test
    void shouldGroupCrossSourceSimilarTitles() {
        Article a = createArticle("id-1", "연합뉴스", "한국은행 기준금리 0.25%p 인하 결정", "https://a.com/1");
        Article b = createArticle("id-2", "한국경제", "한국은행 기준금리 0.25%p 인하 결정", "https://b.com/2");

        DuplicateRemover.DeduplicationResult result = remover.removeDuplicates(List.of(a, b));

        assertEquals(1, result.unique().size());
        assertEquals(1, result.duplicates().size());
    }

    @Test
    void shouldCalculateSimilarityCorrectly() {
        assertEquals(1.0, DuplicateRemover.calculateSimilarity("abc", "abc"));
        assertEquals(0.0, DuplicateRemover.calculateSimilarity("", "abc"));
        assertTrue(DuplicateRemover.calculateSimilarity("기준금리인하결정", "기준금리인하결정보도") > 0.8);
    }

    @Test
    void shouldCleanTitleText() {
        assertEquals("금리인하결정", DuplicateRemover.cleanTitleText("[속보] 금리 인하 결정"));
        assertEquals("금리인하", DuplicateRemover.cleanTitleText("(종합) 금리 인하"));
    }

    @Test
    void shouldKeepDifferentArticlesFromSameSource() {
        Article a = createArticle("id-1", "연합뉴스", "금리 인하 결정", "https://a.com/1");
        Article b = createArticle("id-2", "연합뉴스", "부동산 시장 전망", "https://a.com/2");

        DuplicateRemover.DeduplicationResult result = remover.removeDuplicates(List.of(a, b));

        assertEquals(2, result.unique().size());
    }

    private Article createArticle(String id, String source, String title, String url) {
        return new Article(id, title, "요약 내용입니다", source,
                ArticleSourceType.NEWS_MEDIA,
                OffsetDateTime.of(2026, 7, 20, 12, 0, 0, 0, KST),
                OffsetDateTime.now(KST),
                url, List.of(NewsCategory.INTEREST_RATE), "ko", null);
    }
}
