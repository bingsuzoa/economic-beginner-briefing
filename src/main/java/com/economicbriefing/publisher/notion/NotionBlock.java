package com.economicbriefing.publisher.notion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a Notion API block object for page content creation.
 * Each block is serialized to JSON matching the Notion API block format.
 */
public record NotionBlock(
    String object,
    String type,
    Map<String, Object> content
) {
    private static final int MAX_RICH_TEXT_LENGTH = 2000;

    public static NotionBlock heading2(String text) {
        return new NotionBlock("block", "heading_2",
                Map.of("heading_2", Map.of("rich_text", richText(text))));
    }

    public static NotionBlock heading3(String text) {
        return new NotionBlock("block", "heading_3",
                Map.of("heading_3", Map.of("rich_text", richText(text))));
    }

    public static NotionBlock paragraph(String text) {
        return new NotionBlock("block", "paragraph",
                Map.of("paragraph", Map.of("rich_text", richText(text))));
    }

    public static NotionBlock bulletedListItem(String text) {
        return new NotionBlock("block", "bulleted_list_item",
                Map.of("bulleted_list_item", Map.of("rich_text", richText(text))));
    }

    public static NotionBlock bulletedListItem(String text, String url) {
        return new NotionBlock("block", "bulleted_list_item",
                Map.of("bulleted_list_item", Map.of("rich_text", richTextWithLink(text, url))));
    }

    public static NotionBlock divider() {
        return new NotionBlock("block", "divider", Map.of("divider", Map.of()));
    }

    static List<Map<String, Object>> richText(String content) {
        List<String> chunks = splitText(content);
        return chunks.stream()
                .map(chunk -> Map.<String, Object>of(
                        "type", "text",
                        "text", Map.of("content", chunk)
                ))
                .toList();
    }

    static List<Map<String, Object>> richTextWithLink(String content, String url) {
        List<String> chunks = splitText(content);
        return chunks.stream()
                .map(chunk -> Map.<String, Object>of(
                        "type", "text",
                        "text", Map.of("content", chunk, "link", Map.of("url", url))
                ))
                .toList();
    }

    private static List<String> splitText(String content) {
        if (content.isEmpty()) {
            return List.of("");
        }
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < content.length(); start += MAX_RICH_TEXT_LENGTH) {
            chunks.add(content.substring(start, Math.min(start + MAX_RICH_TEXT_LENGTH, content.length())));
        }
        return chunks;
    }
}
