package com.economicbriefing.collector.mock;

import java.time.LocalDate;

import com.economicbriefing.collector.dto.CollectNewsRequest;
import com.economicbriefing.collector.dto.CollectNewsResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockNewsCollectorTest {

    private final MockNewsCollector collector = new MockNewsCollector();

    @Test
    void shouldReturn3MockArticles() {
        CollectNewsRequest request = CollectNewsRequest.of(LocalDate.of(2026, 7, 20));
        CollectNewsResult result = collector.collect(request);

        assertEquals(3, result.articles().size());
        assertEquals(3, result.totalCollected());
        assertEquals(3, result.totalAccepted());
        assertEquals(0, result.totalRejected());
    }

    @Test
    void shouldReturn3SourceReports() {
        CollectNewsRequest request = CollectNewsRequest.of(LocalDate.of(2026, 7, 20));
        CollectNewsResult result = collector.collect(request);

        assertEquals(3, result.sourceReports().size());
    }

    @Test
    void shouldSetCorrectTargetDate() {
        LocalDate targetDate = LocalDate.of(2026, 7, 18);
        CollectNewsRequest request = CollectNewsRequest.of(targetDate);
        CollectNewsResult result = collector.collect(request);

        assertEquals(targetDate, result.targetDate());
    }

    @Test
    void mockArticlesShouldHaveExpectedIds() {
        CollectNewsRequest request = CollectNewsRequest.of(LocalDate.of(2026, 7, 20));
        CollectNewsResult result = collector.collect(request);

        assertEquals("mock-article-001", result.articles().get(0).id());
        assertEquals("mock-article-002", result.articles().get(1).id());
        assertEquals("mock-article-003", result.articles().get(2).id());
    }

    @Test
    void allArticlesShouldBeKorean() {
        CollectNewsRequest request = CollectNewsRequest.of(LocalDate.of(2026, 7, 20));
        CollectNewsResult result = collector.collect(request);

        result.articles().forEach(article -> assertEquals("ko", article.language()));
    }
}
