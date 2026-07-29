package com.economicbriefing.domain.briefing;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.economicbriefing.domain.analysis.AnalyzedNews;
import com.economicbriefing.domain.analysis.EconomicTerm;
import com.economicbriefing.domain.analysis.NewsEvidenceStatus;
import com.economicbriefing.domain.analysis.SourceReference;
import com.economicbriefing.domain.article.NewsCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BriefingTest {

    @Test
    void shouldCreateBriefingRecord() {
        LocalDate targetDate = LocalDate.of(2026, 7, 20);
        OffsetDateTime generatedAt = OffsetDateTime.now();

        SourceReference source = new SourceReference(
            "art-1", "연합뉴스", "금리 인하 결정",
            "https://example.com/1", generatedAt, true
        );

        EconomicTerm term = new EconomicTerm(
            "기준금리", "한국은행이 정하는 기본 금리", "현재 3.5%"
        );

        AnalyzedNews news = new AnalyzedNews(
            "news-1", "금리가 내려갔어요",
            NewsCategory.INTEREST_RATE, 5,
            List.of("핵심 1", "핵심 2", "핵심 3"),
            "한국은행이 기준금리를 인하했습니다",
            "경기 둔화 우려",
            "경기가 나빠질 것 같아서 금리를 낮췄어요",
            "시중금리 하락",
            "대출 이자 감소",
            List.of("변동금리 대출자"),
            "대출 이자 부담 감소",
            "예금 이자 수입 감소",
            List.of(),
            List.of(term),
            NewsEvidenceStatus.CONFIRMED,
            List.of(),
            List.of(source)
        );

        BriefingMetadata metadata = new BriefingMetadata(50, 30, 5, "gpt-4o", "v1");

        Briefing briefing = new Briefing(
            "briefing-2026-07-20", targetDate, generatedAt,
            "2026-07-20 경제 브리핑",
            List.of("오늘의 핵심: 금리 인하"),
            List.of(news),
            List.of(term),
            metadata
        );

        assertEquals("briefing-2026-07-20", briefing.id());
        assertEquals(targetDate, briefing.targetDate());
        assertEquals("2026-07-20 경제 브리핑", briefing.title());
        assertEquals(1, briefing.news().size());
        assertEquals(1, briefing.glossary().size());
        assertEquals(5, briefing.metadata().selectedNewsCount());
        assertEquals("gpt-4o", briefing.metadata().modelName());
    }

    @Test
    void shouldCreateBriefingWithEmptyLists() {
        Briefing briefing = new Briefing(
            "briefing-empty",
            LocalDate.of(2026, 7, 20),
            OffsetDateTime.now(),
            "빈 브리핑",
            List.of(),
            List.of(),
            List.of(),
            new BriefingMetadata(0, 0, 0, null, null)
        );

        assertTrue(briefing.news().isEmpty());
        assertTrue(briefing.glossary().isEmpty());
        assertTrue(briefing.overallSummary().isEmpty());
        assertNull(briefing.metadata().modelName());
    }
}
