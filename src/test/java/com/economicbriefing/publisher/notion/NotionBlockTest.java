package com.economicbriefing.publisher.notion;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotionBlockTest {

    @Test
    void shouldCreateHeading2Block() {
        NotionBlock block = NotionBlock.heading2("제목");

        assertEquals("block", block.object());
        assertEquals("heading_2", block.type());
        assertTextContent(block, "heading_2", "제목");
    }

    @Test
    void shouldCreateHeading3Block() {
        NotionBlock block = NotionBlock.heading3("소제목");

        assertEquals("heading_3", block.type());
        assertTextContent(block, "heading_3", "소제목");
    }

    @Test
    void shouldCreateParagraphBlock() {
        NotionBlock block = NotionBlock.paragraph("본문 텍스트");

        assertEquals("paragraph", block.type());
        assertTextContent(block, "paragraph", "본문 텍스트");
    }

    @Test
    void shouldCreateBulletedListItemBlock() {
        NotionBlock block = NotionBlock.bulletedListItem("항목");

        assertEquals("bulleted_list_item", block.type());
        assertTextContent(block, "bulleted_list_item", "항목");
    }

    @Test
    void shouldCreateBulletedListItemWithLink() {
        NotionBlock block = NotionBlock.bulletedListItem("링크 텍스트", "https://example.com");

        assertEquals("bulleted_list_item", block.type());
        assertTextContentWithLink(block, "bulleted_list_item", "링크 텍스트", "https://example.com");
    }

    @Test
    void shouldCreateDividerBlock() {
        NotionBlock block = NotionBlock.divider();

        assertEquals("block", block.object());
        assertEquals("divider", block.type());
    }

    @Test
    void shouldSplitLongTextIntoChunks() {
        String longText = "A".repeat(4500);
        List<Map<String, Object>> richText = NotionBlock.richText(longText);

        assertEquals(3, richText.size()); // 2000 + 2000 + 500
        for (Map<String, Object> rt : richText) {
            @SuppressWarnings("unchecked")
            Map<String, Object> textMap = (Map<String, Object>) rt.get("text");
            String content = (String) textMap.get("content");
            assertTrue(content.length() <= 2000);
        }
    }

    @Test
    void shouldHandleEmptyText() {
        List<Map<String, Object>> richText = NotionBlock.richText("");

        assertEquals(1, richText.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> textMap = (Map<String, Object>) richText.get(0).get("text");
        assertEquals("", textMap.get("content"));
    }

    @SuppressWarnings("unchecked")
    private void assertTextContent(NotionBlock block, String blockType, String expectedText) {
        Map<String, Object> typeContent = (Map<String, Object>) block.content().get(blockType);
        List<Map<String, Object>> richText = (List<Map<String, Object>>) typeContent.get("rich_text");
        Map<String, Object> textMap = (Map<String, Object>) richText.get(0).get("text");
        assertEquals(expectedText, textMap.get("content"));
    }

    @SuppressWarnings("unchecked")
    private void assertTextContentWithLink(NotionBlock block, String blockType,
                                            String expectedText, String expectedUrl) {
        Map<String, Object> typeContent = (Map<String, Object>) block.content().get(blockType);
        List<Map<String, Object>> richText = (List<Map<String, Object>>) typeContent.get("rich_text");
        Map<String, Object> textMap = (Map<String, Object>) richText.get(0).get("text");
        assertEquals(expectedText, textMap.get("content"));
        Map<String, Object> link = (Map<String, Object>) textMap.get("link");
        assertEquals(expectedUrl, link.get("url"));
    }
}
