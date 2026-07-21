package com.economicbriefing.analyzer.openai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.economicbriefing.config.OpenAiProperties;
import com.economicbriefing.exception.AnalyzeException;
import com.economicbriefing.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "briefing.dry-run", havingValue = "false")
public class OpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties properties;

    public OpenAiClient(OpenAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
    }

    public String complete(String systemPrompt, String userPrompt) {
        try {
            String requestBody = buildRequestBody(systemPrompt, userPrompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .timeout(properties.timeout())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            log.info("Calling OpenAI API with model={}", properties.model());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                boolean retryable = response.statusCode() == 429
                        || response.statusCode() == 500
                        || response.statusCode() == 503;
                ErrorCode errorCode = retryable ? ErrorCode.ANALYZE_API_ERROR : ErrorCode.ANALYZE_API_ERROR;
                AnalyzeException ex = new AnalyzeException(errorCode);
                log.error("OpenAI API error: status={} body={}", response.statusCode(), response.body());
                throw ex;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");

            if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
                throw new AnalyzeException(ErrorCode.ANALYZE_API_ERROR);
            }

            log.info("OpenAI API call succeeded");
            return content.asText();

        } catch (AnalyzeException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            log.error("OpenAI API timeout", e);
            throw new AnalyzeException(ErrorCode.ANALYZE_TIMEOUT, e);
        } catch (IOException | InterruptedException e) {
            log.error("OpenAI API connection error", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AnalyzeException(ErrorCode.ANALYZE_TIMEOUT, e);
        }
    }

    private String buildRequestBody(String systemPrompt, String userPrompt) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", properties.model());
            root.put("temperature", properties.temperature());

            ObjectNode responseFormat = objectMapper.createObjectNode();
            responseFormat.put("type", "json_object");
            root.set("response_format", responseFormat);

            ArrayNode messages = objectMapper.createArrayNode();

            ObjectNode systemMessage = objectMapper.createObjectNode();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.add(systemMessage);

            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", userPrompt);
            messages.add(userMessage);

            root.set("messages", messages);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new AnalyzeException(ErrorCode.ANALYZE_API_ERROR, e);
        }
    }
}
