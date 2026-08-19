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
    private static final java.util.regex.Pattern RETRY_HINT =
            java.util.regex.Pattern.compile("try again in ([0-9.]+)(ms|s)");

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
        return complete(systemPrompt, userPrompt, properties.model());
    }

    public String complete(String systemPrompt, String userPrompt, double temperature) {
        return complete(systemPrompt, userPrompt, properties.model(), temperature);
    }

    /**
     * Model override: the teacher stage runs on a cheaper model so it does not eat the
     * main model's token-per-minute budget before the analysis call needs it.
     */
    public String complete(String systemPrompt, String userPrompt, String model) {
        return complete(systemPrompt, userPrompt, model, properties.temperature());
    }

    private String complete(String systemPrompt, String userPrompt, String model, double temperature) {
        try {
            String requestBody = buildRequestBody(systemPrompt, userPrompt, model, temperature);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .timeout(properties.timeout())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            log.info("Calling OpenAI API with model={}", model);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("OpenAI API error: status={} body={}", response.statusCode(), response.body());
                Duration retryAfter = response.statusCode() == 429
                        ? parseRetryAfter(response)
                        : null;
                throw new AnalyzeException(ErrorCode.ANALYZE_API_ERROR, retryAfter);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");

            if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
                throw new AnalyzeException(ErrorCode.ANALYZE_API_ERROR);
            }

            String rawContent = content.asText();
            log.info("OpenAI API call succeeded");
            log.info("=== RAW RESPONSE START ===");
            log.info(rawContent);
            log.info("=== RAW RESPONSE END ===");
            return rawContent;

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

    /**
     * How long to wait after a 429. Prefers the Retry-After headers; falls back to the
     * "Please try again in 7.894s" hint OpenAI puts in the error message.
     * A fixed retry delay is useless against a TPM limit, so this must be honoured.
     */
    static Duration parseRetryAfter(HttpResponse<String> response) {
        Duration fromHeader = response.headers().firstValue("retry-after-ms")
                .map(v -> parseLongOrNull(v))
                .filter(ms -> ms != null)
                .map(Duration::ofMillis)
                .orElseGet(() -> response.headers().firstValue("retry-after")
                        .map(OpenAiClient::parseLongOrNull)
                        .filter(s -> s != null)
                        .map(Duration::ofSeconds)
                        .orElse(null));
        if (fromHeader != null) {
            return fromHeader.plusMillis(500);
        }

        var matcher = RETRY_HINT.matcher(response.body() != null ? response.body() : "");
        if (matcher.find()) {
            double seconds = Double.parseDouble(matcher.group(1));
            if (matcher.group(2).equals("ms")) {
                seconds /= 1000d;
            }
            // margin: the TPM window must actually roll over before we retry
            return Duration.ofMillis((long) (seconds * 1000) + 500);
        }
        return null;
    }

    private static Long parseLongOrNull(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String buildRequestBody(String systemPrompt, String userPrompt, String model, double temperature) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", model);
            root.put("temperature", temperature);

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
