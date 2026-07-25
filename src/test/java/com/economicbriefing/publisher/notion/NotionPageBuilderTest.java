package com.economicbriefing.publisher.notion;

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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotionPageBuilderTest {

    @Test
    void shouldBuildBlocksWithOverallSummary() {
        Briefing briefing = createBriefing();
        List<NotionBlock> blocks = NotionPageBuilder.buildBriefingBlocks(briefing);

        // First block should be heading "오늘의 핵심 요약"
        assertEquals("heading_2", blocks.get(0).type());
        // Followed by bullet list items
        assertEquals("bulleted_list_item", blocks.get(1).type());
        assertEquals("bulleted_list_item", blocks.get(2).type());
    }

    @Test
    void shouldBuildBlocksWithNewsSection() {
        Briefing briefing = createBriefing();
        List<NotionBlock> blocks = NotionPageBuilder.buildBriefingBlocks(briefing);

        // Should contain "주요 뉴스" heading
        boolean hasNewsHeading = blocks.stream()
                .anyMatch(b -> "heading_2".equals(b.type()) && containsText(b, "주요 뉴스"));
        assertTrue(hasNewsHeading);

        // Should have divider before news
        boolean hasDivider = blocks.stream().anyMatch(b -> "divider".equals(b.type()));
        assertTrue(hasDivider);
    }

    @Test
    void shouldBuild12SectionBlocks() {
        Briefing briefing = createBriefing();
        List<NotionBlock> blocks = NotionPageBuilder.buildBriefingBlocks(briefing);

        // Should have section headings for the 12-section structure
        assertTrue(blocks.stream().anyMatch(b -> "heading_3".equals(b.type()) && containsText(b, "무슨 일이 있었나요?")));
        assertTrue(blocks.stream().anyMatch(b -> "heading_3".equals(b.type()) && containsText(b, "왜 이런 일이 생겼나요?")));
        assertTrue(blocks.stream().anyMatch(b -> "heading_3".equals(b.type()) && containsText(b, "경제에는 어떤 영향을 주나요?")));
        assertTrue(blocks.stream().anyMatch(b -> "heading_3".equals(b.type()) && containsText(b, "우리 생활에는 어떤 영향이 있나요?")));
    }

    @Test
    void shouldBuildGlossarySection() {
        Briefing briefing = createBriefing();
        List<NotionBlock> blocks = NotionPageBuilder.buildBriefingBlocks(briefing);

        boolean hasGlossaryHeading = blocks.stream()
                .anyMatch(b -> "heading_2".equals(b.type()) && containsText(b, "경제용어"));
        assertTrue(hasGlossaryHeading);
    }

    @Test
    void shouldBuildMetadataSection() {
        Briefing briefing = createBriefing();
        List<NotionBlock> blocks = NotionPageBuilder.buildBriefingBlocks(briefing);

        boolean hasMetaHeading = blocks.stream()
                .anyMatch(b -> "heading_2".equals(b.type()) && containsText(b, "브리핑 정보"));
        assertTrue(hasMetaHeading);

        boolean hasModelInfo = blocks.stream()
                .anyMatch(b -> "paragraph".equals(b.type()) && containsText(b, "AI 모델"));
        assertTrue(hasModelInfo);
    }

    @Test
    void shouldHandleEmptyGlossary() {
        Briefing briefing = createBriefingWithGlossary(List.of());
        List<NotionBlock> blocks = NotionPageBuilder.buildBriefingBlocks(briefing);

        boolean hasEmptyMessage = blocks.stream()
                .anyMatch(b -> "paragraph".equals(b.type())
                        && containsText(b, "정리된 경제용어가 없습니다."));
        assertTrue(hasEmptyMessage);
    }

    @Test
    void shouldIncludeSourceLinks() {
        Briefing briefing = createBriefing();
        List<NotionBlock> blocks = NotionPageBuilder.buildBriefingBlocks(briefing);

        boolean hasSourceBlock = blocks.stream()
                .anyMatch(b -> "bulleted_list_item".equals(b.type())
                        && containsText(b, "대표 출처"));
        assertTrue(hasSourceBlock);
    }

    @Test
    void shouldBuildNonEmptyBlockList() {
        Briefing briefing = createBriefing();
        List<NotionBlock> blocks = NotionPageBuilder.buildBriefingBlocks(briefing);

        assertFalse(blocks.isEmpty());
        assertTrue(blocks.size() >= 10);
    }

    @Test
    void shouldIncludeGlossaryWithExample() {
        EconomicTerm termWithExample = new EconomicTerm("DSR", "총부채원리금상환비율", "연소득 5천만원일 때");
        Briefing briefing = createBriefingWithGlossary(List.of(termWithExample));
        List<NotionBlock> blocks = NotionPageBuilder.buildBriefingBlocks(briefing);

        boolean hasExampleText = blocks.stream()
                .anyMatch(b -> "bulleted_list_item".equals(b.type())
                        && containsText(b, "예: 연소득 5천만원일 때"));
        assertTrue(hasExampleText);
    }

    @SuppressWarnings("unchecked")
    private boolean containsText(NotionBlock block, String searchText) {
        try {
            var contentMap = block.content();
            for (Object value : contentMap.values()) {
                if (value instanceof java.util.Map<?, ?> innerMap) {
                    Object richText = innerMap.get("rich_text");
                    if (richText instanceof List<?> rtList) {
                        for (Object rt : rtList) {
                            if (rt instanceof java.util.Map<?, ?> rtMap) {
                                Object textObj = rtMap.get("text");
                                if (textObj instanceof java.util.Map<?, ?> textMap) {
                                    Object content = textMap.get("content");
                                    if (content instanceof String s && s.contains(searchText)) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    private AnalyzedNews createAnalyzedNews() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(9));
        return new AnalyzedNews(
                "news-1", "기준금리가 내려갔어요", NewsCategory.INTEREST_RATE, 5,
                List.of("핵심 1", "핵심 2", "핵심 3"),
                "기준금리가 인하되었습니다.", "경기 둔화 우려",
                "시중금리 하락", "대출 이자 감소",
                List.of("변동금리 대출자"),
                "대출 이자 부담 감소", "예금 이자 수입 감소",
                List.of(),
                List.of(new EconomicTerm("기준금리", "한국은행이 정하는 금리", null)),
                NewsEvidenceStatus.CONFIRMED,
                List.of(),
                List.of(new SourceReference("article-1", "한국은행", "기준금리 인하 결정",
                        "https://example.com/1", now, true))
        );
    }

    private Briefing createBriefing() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(9));
        return new Briefing(
                "briefing-2025-01-15",
                LocalDate.of(2025, 1, 15),
                now,
                "2025-01-15 경제 브리핑",
                List.of("요약 1", "요약 2"),
                List.of(createAnalyzedNews()),
                List.of(new EconomicTerm("기준금리", "한국은행이 정하는 금리", null)),
                new BriefingMetadata(10, 10, 1, "gpt-4o", "v2")
        );
    }

    private Briefing createBriefingWithGlossary(List<EconomicTerm> glossary) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(9));
        return new Briefing(
                "briefing-2025-01-15",
                LocalDate.of(2025, 1, 15),
                now,
                "2025-01-15 경제 브리핑",
                List.of("요약"),
                List.of(new AnalyzedNews(
                        "news-1", "제목", NewsCategory.OTHER, 3,
                        List.of("핵심"),
                        "사건", "원인", "경제영향", "생활영향",
                        List.of(), "긍정", "부정", List.of(),
                        List.of(),
                        NewsEvidenceStatus.CONFIRMED,
                        List.of(),
                        List.of(new SourceReference("article-1", "출처", "제목",
                                "https://example.com", now, true))
                )),
                glossary,
                new BriefingMetadata(5, 5, 1, "gpt-4o", "v2")
        );
    }
}
