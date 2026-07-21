package com.economicbriefing.collector.filter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DateFilterTest {

    private final DateFilter dateFilter = new DateFilter();
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @Test
    void shouldAcceptArticlesWithinDateRange() {
        OffsetDateTime start = OffsetDateTime.of(2026, 7, 20, 0, 0, 0, 0, KST);
        OffsetDateTime end = OffsetDateTime.of(2026, 7, 20, 23, 59, 59, 0, KST);

        Article inRange = createArticle("1", OffsetDateTime.of(2026, 7, 20, 12, 0, 0, 0, KST));

        DateFilter.DateFilterResult result = dateFilter.filter(List.of(inRange), start, end);

        assertEquals(1, result.accepted().size());
        assertEquals(0, result.rejected().size());
    }

    @Test
    void shouldRejectArticlesOutsideDateRange() {
        OffsetDateTime start = OffsetDateTime.of(2026, 7, 20, 0, 0, 0, 0, KST);
        OffsetDateTime end = OffsetDateTime.of(2026, 7, 20, 23, 59, 59, 0, KST);

        Article before = createArticle("1", OffsetDateTime.of(2026, 7, 19, 23, 0, 0, 0, KST));
        Article after = createArticle("2", OffsetDateTime.of(2026, 7, 21, 1, 0, 0, 0, KST));

        DateFilter.DateFilterResult result = dateFilter.filter(List.of(before, after), start, end);

        assertEquals(0, result.accepted().size());
        assertEquals(2, result.rejected().size());
    }

    @Test
    void shouldAcceptArticlesAtBoundary() {
        OffsetDateTime start = OffsetDateTime.of(2026, 7, 20, 12, 0, 0, 0, KST);
        OffsetDateTime end = OffsetDateTime.of(2026, 7, 20, 12, 59, 59, 0, KST);

        Article atStart = createArticle("1", start);
        Article atEnd = createArticle("2", end);

        DateFilter.DateFilterResult result = dateFilter.filter(List.of(atStart, atEnd), start, end);

        assertEquals(2, result.accepted().size());
    }

    private Article createArticle(String id, OffsetDateTime publishedAt) {
        return new Article(id, "테스트 제목", "요약", "테스트소스",
                ArticleSourceType.NEWS_MEDIA, publishedAt, OffsetDateTime.now(KST),
                "https://example.com/" + id, List.of(NewsCategory.OTHER), "ko", null);
    }
}
