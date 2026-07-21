package com.economicbriefing.publisher.mock;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.economicbriefing.domain.analysis.AnalyzedNews;
import com.economicbriefing.domain.analysis.EconomicTerm;
import com.economicbriefing.domain.analysis.NewsEvidenceStatus;
import com.economicbriefing.domain.analysis.SourceReference;
import com.economicbriefing.domain.article.NewsCategory;
import com.economicbriefing.domain.briefing.Briefing;
import com.economicbriefing.domain.briefing.BriefingMetadata;
import com.economicbriefing.publisher.dto.PublishBriefingRequest;
import com.economicbriefing.publisher.dto.PublishBriefingResult;
import com.economicbriefing.publisher.dto.PublishChannelResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockBriefingPublisherTest {

    private MockBriefingPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new MockBriefingPublisher();
    }

    @Test
    void shouldPublishSuccessfully() {
        Briefing briefing = createBriefing();
        PublishBriefingRequest request = new PublishBriefingRequest(briefing, false);

        PublishBriefingResult result = publisher.publish(request);

        assertEquals(briefing.id(), result.briefingId());
        assertEquals(1, result.results().size());
        assertEquals(PublishChannelResult.Status.SUCCESS, result.results().get(0).status());
        assertEquals("mock", result.results().get(0).channel());
        assertEquals("mock-ext-" + briefing.id(), result.results().get(0).externalId());
        assertNotNull(result.completedAt());
    }

    @Test
    void shouldSkipInDryRunMode() {
        Briefing briefing = createBriefing();
        PublishBriefingRequest request = new PublishBriefingRequest(briefing, true);

        PublishBriefingResult result = publisher.publish(request);

        assertEquals(PublishChannelResult.Status.SKIPPED, result.results().get(0).status());
        assertNull(result.results().get(0).externalId());
    }

    @Test
    void shouldTrackPublishedBriefings() {
        Briefing briefing = createBriefing();
        publisher.publish(new PublishBriefingRequest(briefing, false));

        assertEquals(1, publisher.getPublishedBriefings().size());
        assertEquals(briefing.id(), publisher.getPublishedBriefings().get(0).id());
    }

    @Test
    void shouldPublishMultipleBriefings() {
        publisher.publish(new PublishBriefingRequest(createBriefing(), false));
        publisher.publish(new PublishBriefingRequest(createBriefing(), true));

        assertEquals(2, publisher.getPublishedBriefings().size());
    }

    @Test
    void shouldReturnCompletedAtInKST() {
        PublishBriefingResult result = publisher.publish(
                new PublishBriefingRequest(createBriefing(), false));

        assertNotNull(result.completedAt());
        assertEquals(ZoneOffset.ofHours(9), result.completedAt().getOffset());
    }

    private Briefing createBriefing() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(9));
        return new Briefing(
                "briefing-2025-01-15",
                LocalDate.of(2025, 1, 15),
                now,
                "2025-01-15 경제 브리핑",
                List.of("전체 요약 1", "전체 요약 2"),
                List.of(new AnalyzedNews(
                        "news-1", "기준금리 인하", NewsCategory.INTEREST_RATE, 5,
                        "왜 중요한가", null, null,
                        "한 줄 결론", "해설",
                        NewsEvidenceStatus.CONFIRMED, null,
                        List.of(new EconomicTerm("기준금리", "설명", null)),
                        List.of(new SourceReference("article-1", "한국은행", "제목",
                                "https://example.com", now, true))
                )),
                List.of(new EconomicTerm("기준금리", "설명", null)),
                new BriefingMetadata(10, 10, 1, "gpt-4o", "v1")
        );
    }
}
