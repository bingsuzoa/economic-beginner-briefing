package com.economicbriefing.briefing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.economicbriefing.article.ArticleEntity;
import com.economicbriefing.article.ArticleRepository;
import com.economicbriefing.article.ParagraphSplitter;
import com.economicbriefing.article.YonhapBodyFetcher;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.config.OpenAiProperties;
import com.economicbriefing.llm.OpenAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Opt-in, real Java pipeline replay. Never connects to the application's configured database. */
@EnabledIfEnvironmentVariable(named = "BRIEFING_REVIEW_DB", matches = "economic_briefing_review_[a-z0-9_]+")
class BeginnerMemoryReplayTest {
    @Test
    void reviewOneArticle() throws Exception {
        String db = System.getenv("BRIEFING_REVIEW_DB");
        if (db == null || !db.matches("economic_briefing_review_[a-z0-9_]+")) throw new IllegalArgumentException("isolated review database required");
        Path directory = Path.of(System.getenv("BRIEFING_REVIEW_DIR"));
        ObjectMapper json = new ObjectMapper();
        JsonNode fixture = json.readTree(directory.resolve("article.json").toFile());
        ArticleEntity article = article(fixture);
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:postgresql://localhost:5432/" + db + "?stringtype=unspecified",
                System.getProperty("user.name"), ""));
        var properties = new OpenAiProperties(System.getenv("OPENAI_API_KEY"), Duration.ofSeconds(120),
                "gpt-5.6-luna", "gpt-5.6-luna", "gpt-5.6-terra", "gpt-5.6-luna", "text-embedding-3-large");
        var app = new AppProperties(new AppProperties.TimeoutProperties(Duration.ofSeconds(20)),
                new AppProperties.SchedulerProperties(false, "0 5 * * * *", "0 10 5 * * *"),
                new AppProperties.BudgetProperties(50000, 100000, 15000, .14));
        var splitter = new ParagraphSplitter();
        var client = new RecordedClient(properties, json, directory.resolve("cache"));
        var llm = new EconomicFlowLlm(client, properties, json, splitter);
        var observations = new ObservationStore(jdbc, json, splitter);
        var articles = mock(ArticleRepository.class);
        when(articles.findById(anyString())).thenAnswer(call -> {
            List<String> rows = jdbc.query("SELECT row_to_json(a)::text FROM articles a WHERE id = ?", (rs, n) -> rs.getString(1), call.<String>getArgument(0));
            return rows.isEmpty() ? Optional.empty() : Optional.of(article(json.readTree(rows.getFirst())));
        });
        when(articles.save(any())).thenAnswer(call -> {
            ArticleEntity a = call.getArgument(0);
            jdbc.update("UPDATE articles SET body = ?, body_status = ?, body_fetched_at = ? WHERE id = ?",
                    a.getBody(), a.getBodyStatus(), a.getBodyFetchedAt(), a.getId());
            return a;
        });
        var briefings = mock(DailyBriefingRepository.class);
        AtomicReference<DailyBriefingEntity> latest = new AtomicReference<>();
        when(briefings.save(any())).thenAnswer(call -> { latest.set(call.getArgument(0)); return call.getArgument(0); });
        var service = new DailyBriefingService(articles, briefings, new YonhapBodyFetcher(app), splitter, observations,
                new PrincipleStore(jdbc), llm, client, properties, app, json);
        LocalDate date = LocalDate.parse(System.getenv("BRIEFING_REVIEW_DATE"));
        try {
            DailyBriefingEntity result = service.review(date, article, Boolean.parseBoolean(System.getenv("BRIEFING_REVIEW_SELECTED")));
            assertEquals("SUCCESS", result.getStatus());
            if (!observations.findReusable(article, properties.extractionModel(), EconomicFlowLlm.EXTRACTION_PROMPT_VERSION).isEmpty()) {
                ArticleEntity revised = article(fixture);
                revised.setBody(article.getBody() + "\n수정된 기사 본문입니다.");
                assertTrue(observations.findReusable(revised, properties.extractionModel(), EconomicFlowLlm.EXTRACTION_PROMPT_VERSION).isEmpty());
                assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                        "UPDATE article_observations SET memory_eligible = true, source_snapshot = NULL WHERE article_id = ?", article.getId()));
            }
        } finally {
            DailyBriefingEntity result = latest.get();
            if (result != null) {
                var output = json.createObjectNode();
                output.put("articleId", article.getId()); output.put("targetDate", date.toString());
                output.put("status", result.getStatus()); output.put("error", result.getErrorMessage());
                output.put("pipelineVersion", result.getPipelineVersion());
                output.put("sourceHash", ObservationStore.bodyHash(article.getBody()));
                output.set("usage", json.readTree(result.getUsageJson()));
                output.set("trace", json.readTree(result.getTraceJson()));
                if (result.getResultJson() != null) output.set("result", json.readTree(result.getResultJson()));
                Files.writeString(directory.resolve("result.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(output));
            }
        }
    }

    private static ArticleEntity article(JsonNode input) {
        ArticleEntity a = new ArticleEntity();
        a.setId(input.path("id").asText()); a.setTitle(input.path("title").asText());
        a.setSummary(input.path("summary").asText()); a.setUrl(input.path("url").asText());
        a.setSource(input.path("source").asText()); a.setSourceArticleId(input.path("source_article_id").asText());
        a.setBody(input.path("body").asText()); a.setBodyStatus(input.path("body_status").asText());
        a.setPublishedAt(OffsetDateTime.parse(input.path("published_at").asText()));
        if (!input.path("body_fetched_at").isNull()) a.setBodyFetchedAt(OffsetDateTime.parse(input.path("body_fetched_at").asText()));
        return a;
    }

    /** Cache actual API results by every request parameter; reuse consumes zero new API tokens. */
    private static class RecordedClient extends OpenAiClient {
        private final ObjectMapper json;
        private final Path cache;
        private final OpenAiProperties properties;
        RecordedClient(OpenAiProperties properties, ObjectMapper json, Path cache) throws Exception {
            super(properties, json); this.properties = properties; this.json = json; this.cache = cache; Files.createDirectories(cache);
        }
        @Override
        public LlmResult complete(String model, String instructions, String input, String reasoning,
                String verbosity, String schemaName, JsonNode schema, int maxOutputTokens) {
            try {
                var request = json.createObjectNode().put("model", model).put("instructions", instructions).put("input", input)
                        .put("reasoning", reasoning).put("verbosity", verbosity).put("schemaName", schemaName).put("maxOutputTokens", maxOutputTokens);
                request.set("schema", schema);
                Path file = cache.resolve(schemaName + "-" + ObservationStore.bodyHash(request.toString()) + ".json");
                if (Files.exists(file)) return new LlmResult(json.readTree(file.toFile()).path("response").path("value"), new Usage(0, 0, 0, 0));
                LlmResult response = super.complete(model, instructions, input, reasoning, verbosity, schemaName, schema, maxOutputTokens);
                var saved = json.createObjectNode(); saved.set("request", request); saved.set("response", json.valueToTree(response));
                Files.writeString(file, json.writerWithDefaultPrettyPrinter().writeValueAsString(saved));
                return response;
            } catch (Exception e) { throw new IllegalStateException("review API request failed", e); }
        }
        @Override
        public EmbeddingResult embed(List<String> input) {
            try {
                var request = json.createObjectNode().put("model", properties.embeddingModel()).put("dimensions", 1536);
                request.set("input", json.valueToTree(input));
                Path file = cache.resolve("embedding-" + ObservationStore.bodyHash(request.toString()) + ".json");
                if (Files.exists(file)) {
                    var saved = json.treeToValue(json.readTree(file.toFile()).path("response"), EmbeddingResult.class);
                    return new EmbeddingResult(saved.vectors(), 0);
                }
                var response = super.embed(input);
                var saved = json.createObjectNode(); saved.set("request", request); saved.set("response", json.valueToTree(response));
                Files.writeString(file, json.writeValueAsString(saved));
                return response;
            } catch (Exception e) { throw new IllegalStateException("review embedding request failed", e); }
        }
    }
}
