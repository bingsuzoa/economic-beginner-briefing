package com.economicbriefing.classifier;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import com.economicbriefing.classifier.entity.ArticleEmbeddingEntity;
import com.economicbriefing.classifier.repository.ArticleEmbeddingRepository;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.config.OpenAiProperties;
import com.economicbriefing.domain.article.Article;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final String EMBEDDING_API_URL = "https://api.openai.com/v1/embeddings";
    private static final int MAX_INPUT_CHARS = 30000; // ponytail: 단순 truncation

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties openAiProperties;
    private final AppProperties appProperties;
    private final ArticleEmbeddingRepository embeddingRepository;

    public EmbeddingService(ObjectMapper objectMapper, OpenAiProperties openAiProperties,
                            AppProperties appProperties,
                            ArticleEmbeddingRepository embeddingRepository) {
        this.objectMapper = objectMapper;
        this.openAiProperties = openAiProperties;
        this.appProperties = appProperties;
        this.embeddingRepository = embeddingRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(openAiProperties.timeout())
                .build();
    }

    public void embedAndSave(Article article) {
        String model = appProperties.embedding().model();
        int dimensions = appProperties.embedding().dimensions();

        if (embeddingRepository.findByArticleIdAndEmbeddingModel(article.id(), model).isPresent()) {
            log.debug("Embedding already exists: articleId={}, model={}", article.id(), model);
            return;
        }

        String input = buildInput(article);
        float[] vector = embed(input, model, dimensions);

        ArticleEmbeddingEntity entity = new ArticleEmbeddingEntity();
        entity.setArticleId(article.id());
        entity.setEmbeddingVector(toVectorString(vector));
        entity.setEmbeddingModel(model);
        entity.setDimensions(dimensions);
        embeddingRepository.save(entity);

        log.debug("Embedding saved: articleId={}, dimensions={}", article.id(), vector.length);
    }

    /** Creates a query embedding without persisting it. */
    public float[] embed(String input, String model, int dimensions) {
        return callEmbeddingApi(input, model, dimensions);
    }

    private String buildInput(Article article) {
        String input = "제목: " + article.title();
        if (article.content() != null && !article.content().isBlank()) {
            input += "\n\n본문:\n" + article.content();
        }
        if (input.length() > MAX_INPUT_CHARS) {
            input = input.substring(0, MAX_INPUT_CHARS);
        }
        return input;
    }

    private float[] callEmbeddingApi(String input, String model, int dimensions) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("input", input);
            body.put("dimensions", dimensions);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(EMBEDDING_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + openAiProperties.apiKey())
                    .timeout(openAiProperties.timeout())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Embedding API error: status={}", response.statusCode());
                throw new RuntimeException("Embedding API returned " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode embeddingNode = root.path("data").path(0).path("embedding");

            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = (float) embeddingNode.get(i).asDouble();
            }
            if (vector.length != dimensions) {
                throw new RuntimeException("Embedding dimension mismatch: expected " + dimensions + ", got " + vector.length);
            }
            return vector;

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Embedding API call failed", e);
        }
    }

    // ponytail: pgvector text format "[0.1,0.2,...]"
    public static String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    public void embedAll(List<Article> articles) {
        for (Article article : articles) {
            try {
                embedAndSave(article);
            } catch (Exception e) {
                log.warn("Embedding failed for article {}: {}", article.id(), e.getMessage());
                // ponytail: 임베딩 실패는 파이프라인을 중단하지 않음
            }
        }
    }
}
