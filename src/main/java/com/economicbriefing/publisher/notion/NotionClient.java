package com.economicbriefing.publisher.notion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.economicbriefing.config.NotionProperties;
import com.economicbriefing.domain.briefing.Briefing;
import com.economicbriefing.exception.ErrorCode;
import com.economicbriefing.exception.PublishException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "briefing.dry-run", havingValue = "false")
public class NotionClient {

    private static final Logger log = LoggerFactory.getLogger(NotionClient.class);
    private static final String NOTION_API_BASE = "https://api.notion.com/v1";
    private static final String NOTION_VERSION = "2022-06-28";
    private static final int MAX_CHILDREN_PER_REQUEST = 100;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public NotionClient(NotionProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(NOTION_API_BASE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .defaultHeader("Notion-Version", NOTION_VERSION)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public String findPageByBriefingId(String databaseId, String briefingId) {
        Map<String, Object> filter = Map.of(
                "property", "Briefing ID",
                "rich_text", Map.of("equals", briefingId)
        );
        Map<String, Object> body = Map.of(
                "page_size", 1,
                "filter", filter
        );

        try {
            String response = webClient.post()
                    .uri("/databases/{databaseId}/query", databaseId)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("Notion query error: status={} body={}",
                                                clientResponse.statusCode(), errorBody);
                                        return Mono.error(new PublishException(
                                                ErrorCode.PUBLISH_CHANNEL_ERROR, "notion"));
                                    })
                    )
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.path("results");
            if (results.isArray() && !results.isEmpty()) {
                return results.get(0).path("id").asText(null);
            }
            return null;

        } catch (PublishException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to query Notion database", e);
            throw new PublishException(ErrorCode.PUBLISH_CHANNEL_ERROR, "notion", e);
        }
    }

    public String createBriefingPage(String databaseId, Briefing briefing, List<NotionBlock> blocks) {
        List<List<Map<String, Object>>> chunks = chunkBlocks(blocks);
        List<Map<String, Object>> initialChildren = chunks.isEmpty()
                ? List.of() : chunks.get(0);

        Map<String, Object> body = buildCreatePageBody(databaseId, briefing, initialChildren);

        try {
            String response = webClient.post()
                    .uri("/pages")
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("Notion create page error: status={} body={}",
                                                clientResponse.statusCode(), errorBody);
                                        return Mono.error(new PublishException(
                                                ErrorCode.PUBLISH_CHANNEL_ERROR, "notion"));
                                    })
                    )
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            String pageId = root.path("id").asText();

            // Append remaining chunks
            for (int i = 1; i < chunks.size(); i++) {
                appendChildren(pageId, chunks.get(i));
            }

            log.info("Created Notion page: id={}", pageId);
            return pageId;

        } catch (PublishException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create Notion page", e);
            throw new PublishException(ErrorCode.PUBLISH_CHANNEL_ERROR, "notion", e);
        }
    }

    private void appendChildren(String blockId, List<Map<String, Object>> children) {
        Map<String, Object> body = Map.of("children", children);

        webClient.patch()
                .uri("/blocks/{blockId}/children", blockId)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Notion append error: status={} body={}",
                                            clientResponse.statusCode(), errorBody);
                                    return Mono.error(new PublishException(
                                            ErrorCode.PUBLISH_CHANNEL_ERROR, "notion"));
                                })
                )
                .bodyToMono(String.class)
                .block();
    }

    private Map<String, Object> buildCreatePageBody(
            String databaseId, Briefing briefing, List<Map<String, Object>> children) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parent", Map.of("database_id", databaseId));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("Name", Map.of(
                "title", List.of(Map.of(
                        "type", "text",
                        "text", Map.of("content", briefing.title())
                ))
        ));
        properties.put("Briefing ID", Map.of(
                "rich_text", List.of(Map.of(
                        "type", "text",
                        "text", Map.of("content", briefing.id())
                ))
        ));
        properties.put("Target Date", Map.of(
                "date", Map.of("start", briefing.targetDate().toString())
        ));
        properties.put("Generated At", Map.of(
                "date", Map.of("start", briefing.generatedAt().toString())
        ));
        properties.put("News Count", Map.of(
                "number", briefing.news().size()
        ));

        body.put("properties", properties);
        body.put("children", children);

        return body;
    }

    @SuppressWarnings("unchecked")
    private List<List<Map<String, Object>>> chunkBlocks(List<NotionBlock> blocks) {
        List<Map<String, Object>> serialized = blocks.stream()
                .map(block -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("object", block.object());
                    map.put("type", block.type());
                    map.putAll(block.content());
                    return map;
                })
                .toList();

        List<List<Map<String, Object>>> chunks = new ArrayList<>();
        for (int start = 0; start < serialized.size(); start += MAX_CHILDREN_PER_REQUEST) {
            chunks.add(serialized.subList(start,
                    Math.min(start + MAX_CHILDREN_PER_REQUEST, serialized.size())));
        }
        return chunks;
    }

    public static boolean isRetryableStatusCode(int statusCode) {
        return statusCode == 429 || statusCode == 500 || statusCode == 503 || statusCode == 408;
    }
}
