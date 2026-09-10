package com.economicbriefing.llm;

import com.economicbriefing.config.OpenAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OpenAiClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
    private static final URI RESPONSES = URI.create("https://api.openai.com/v1/responses");
    private static final URI EMBEDDINGS = URI.create("https://api.openai.com/v1/embeddings");
    private final OpenAiProperties properties;
    private final ObjectMapper json;
    private final HttpClient http;

    public OpenAiClient(OpenAiProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
    }

    public LlmResult complete(String model, String instructions, String input, String reasoning,
            String verbosity, String schemaName, JsonNode schema, int maxOutputTokens) {
        ObjectNode body = json.createObjectNode();
        body.put("model", model);
        body.put("instructions", instructions);
        body.put("input", input);
        body.putObject("reasoning").put("effort", reasoning);
        ObjectNode text = body.putObject("text");
        text.put("verbosity", verbosity);
        ObjectNode format = text.putObject("format");
        format.put("type", "json_schema");
        format.put("name", schemaName);
        format.put("strict", true);
        format.set("schema", schema);
        body.putObject("prompt_cache_options").put("mode", "explicit");
        body.put("max_output_tokens", maxOutputTokens);
        body.put("store", false);

        JsonNode response = post(RESPONSES, body);
        StringBuilder output = new StringBuilder();
        for (JsonNode item : response.path("output")) {
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText())) output.append(content.path("text").asText());
            }
        }
        if (output.isEmpty()) throw new IllegalStateException("OpenAI returned no structured output");
        try {
            Usage usage = usage(response.path("usage"));
            log.info("OpenAI completed: model={}, inputTokens={}, cachedTokens={}, outputTokens={}",
                    model, usage.inputTokens(), usage.cachedInputTokens(), usage.outputTokens());
            return new LlmResult(json.readTree(output.toString()), usage);
        } catch (IOException e) {
            throw new IllegalStateException("OpenAI returned invalid JSON", e);
        }
    }

    public EmbeddingResult embed(List<String> inputs) {
        if (inputs.isEmpty()) return new EmbeddingResult(List.of(), 0);
        ObjectNode body = json.createObjectNode();
        body.put("model", properties.embeddingModel());
        body.put("dimensions", 1536);
        body.put("encoding_format", "float");
        ArrayNode values = body.putArray("input");
        inputs.forEach(values::add);
        JsonNode response = post(EMBEDDINGS, body);
        List<float[]> vectors = new ArrayList<>();
        for (JsonNode item : response.path("data")) {
            JsonNode embedding = item.path("embedding");
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) vector[i] = (float) embedding.get(i).asDouble();
            vectors.add(vector);
        }
        if (vectors.size() != inputs.size()) throw new IllegalStateException("OpenAI embedding count mismatch");
        int tokens = response.path("usage").path("total_tokens").asInt(
                response.path("usage").path("prompt_tokens").asInt());
        log.info("OpenAI embeddings completed: model={}, inputs={}, tokens={}", properties.embeddingModel(), inputs.size(), tokens);
        return new EmbeddingResult(vectors, tokens);
    }

    private JsonNode post(URI uri, JsonNode body) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(properties.timeout())
                        .header("Authorization", "Bearer " + properties.apiKey())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) return json.readTree(response.body());
                last = new IllegalStateException("OpenAI HTTP " + response.statusCode() + ": " + errorMessage(response.body()));
                if (attempt == 2 || (response.statusCode() != 429 && response.statusCode() < 500)) throw last;
                pause(retryAfter(response));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("OpenAI call interrupted", e);
            } catch (IOException e) {
                last = new IllegalStateException("OpenAI connection failed", e);
                if (attempt == 2) throw last;
                pause(Duration.ofSeconds(1));
            }
        }
        throw last == null ? new IllegalStateException("OpenAI call failed") : last;
    }

    private String errorMessage(String body) {
        try {
            String message = json.readTree(body).path("error").path("message").asText("request failed");
            return message.substring(0, Math.min(message.length(), 500));
        } catch (Exception e) {
            return "request failed";
        }
    }

    private static Duration retryAfter(HttpResponse<?> response) {
        long seconds = response.headers().firstValue("retry-after").map(value -> {
            try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return 1L; }
        }).orElse(1L);
        return Duration.ofSeconds(Math.min(seconds, 60));
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI retry interrupted", e);
        }
    }

    private static Usage usage(JsonNode node) {
        return new Usage(node.path("input_tokens").asInt(),
                node.path("input_tokens_details").path("cache_write_tokens").asInt(),
                node.path("input_tokens_details").path("cached_tokens").asInt(),
                node.path("output_tokens").asInt());
    }

    public record Usage(int inputTokens, int cacheWriteInputTokens, int cachedInputTokens, int outputTokens) {}
    public record LlmResult(JsonNode value, Usage usage) {}
    public record EmbeddingResult(List<float[]> vectors, int inputTokens) {}
}
