package com.economicbriefing.briefing;

import com.economicbriefing.article.*;
import com.economicbriefing.config.*;
import com.economicbriefing.llm.OpenAiClient;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Paid, opt-in replay: real canonicalization, ALL title batches/merge, selection and cost guards.
 * Post-selection work is stubbed explicitly; this is never a full-pipeline quality PASS. */
@EnabledIfEnvironmentVariable(named = "MACRO_REPLAY_FIXTURE", matches = ".+")
class MacroSelectionReplayTest {
    @Test void replayFrozenWindows() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        Path fixture = Path.of(System.getenv("MACRO_REPLAY_FIXTURE"));
        Path output = Path.of(System.getenv("MACRO_REPLAY_OUTPUT"));
        Files.createDirectory(output); // Never overwrite an attempt.
        Path cache = output.getParent().resolve("request-cache");
        Files.createDirectories(cache);
        var properties = new OpenAiProperties(System.getenv("OPENAI_API_KEY"), Duration.ofSeconds(240),
                "gpt-5.6-luna", "gpt-5.6-luna", "gpt-5.6-terra", "gpt-5.6-luna", "text-embedding-3-large");
        var app = new AppProperties(new AppProperties.TimeoutProperties(Duration.ofSeconds(30)),
                new AppProperties.SchedulerProperties(false, "0 5 * * * *", "0 10 5 * * *"),
                new AppProperties.BudgetProperties(50000, 100000, 15000, .14));
        for (var sample : json.readTree(fixture.toFile())) {
            LocalDate date = LocalDate.parse(sample.path("date").asText());
            var window = new ArrayList<ArticleEntity>();
            for (var row : sample.path("articles")) {
                var a = article(row);
                if ("true".equals(System.getenv("MACRO_REPLAY_WITH_BODIES")) && row.hasNonNull("body")) {
                    a.setBody(row.path("body").asText()); a.setBodyStatus("FULL_TEXT");
                }
                window.add(a);
            }
            var repository = mock(ArticleRepository.class);
            when(repository.findByPublishedAtGreaterThanEqualAndPublishedAtLessThanOrderByPublishedAtAsc(any(), any())).thenReturn(window);
            when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
            var briefings = mock(DailyBriefingRepository.class);
            var last = new AtomicReference<DailyBriefingEntity>();
            when(briefings.save(any())).thenAnswer(call -> { last.set(call.getArgument(0)); return call.getArgument(0); });
            var client = new RecordedClient(properties, json, cache);
            var captured = new AtomicReference<EconomicFlowLlm.Selection>();
            var llm = new EconomicFlowLlm(client, properties, json, new ParagraphSplitter()) {
                @Override public Call<Selection> select(LocalDate d, List<ArticleEntity> pool) {
                    var call = super.select(d, pool); captured.set(call.value()); return call;
                }
                @Override public Call<List<ObservationStore.Draft>> extract(Map<String, String> paragraphs) {
                    return new Call<>(List.of(), new OpenAiClient.Usage(0, 0, 0, 0));
                }
            };
            // Only selection is evaluated. No web fetch, extraction, embedding or DB write is permitted.
            var fetcher = mock(YonhapBodyFetcher.class);
            when(fetcher.fetchBefore(anyString(), any())).thenThrow(
                    new IllegalStateException("No frozen source; live page fetch disabled"));
            var store = mock(ObservationStore.class);
            when(store.findReusable(any(), anyString(), anyString())).thenReturn(List.of());
            when(store.replace(any(), anyList(), anyString(), anyString())).thenReturn(List.of());
            var service = new DailyBriefingService(repository, briefings, fetcher, new ParagraphSplitter(), store,
                    mock(PrincipleStore.class), llm, client, properties, app, json);
            try {
                var run = service.run(date, "SELECTION_REPLAY_ONLY", true).orElseThrow();
                assertEquals("SUCCESS", run.getStatus());
                assertNotNull(captured.get());
                assertTrue(captured.get().all().size() <= 20);
            } finally {
                var result = json.createObjectNode().put("date", date.toString()).put("mode", "SELECTION_ONLY")
                        .put("fixtureSha256", ObservationStore.bodyHash(Files.readString(fixture)))
                        .put("newEstimatedCostUsd", client.newCost).put("newCalls", client.newCalls).put("cachedCalls", client.cachedCalls);
                if (last.get() != null) {
                    result.put("runId", last.get().getId()).put("inputHash", last.get().getInputHash());
                    result.put("status", last.get().getStatus()).put("error", last.get().getErrorMessage());
                    result.set("trace", json.readTree(last.get().getTraceJson()));
                    result.set("usage", json.readTree(last.get().getUsageJson()));
                }
                result.set("requests", client.requests);
                if (captured.get() != null) {
                    for (String kind : List.of("briefingArticles", "memoryArticles")) {
                        var rows = result.putArray(kind);
                        var selected = kind.equals("briefingArticles") ? captured.get().briefingArticles() : captured.get().memoryArticles();
                        for (var item : selected) rows.addObject().put("articleId", item.article().getId())
                                .put("title", item.article().getTitle()).put("reason", item.reason());
                    }
                }
                json.writerWithDefaultPrettyPrinter().writeValue(output.resolve(date + ".json").toFile(), result);
            }
        }
    }

    static ArticleEntity article(JsonNode row) {
        var a = new ArticleEntity(); a.setId(row.path("id").asText()); a.setSource(row.path("source").asText());
        a.setSourceArticleId(row.path("source_article_id").asText()); a.setUrl(row.path("url").asText());
        a.setTitle(row.path("title").asText()); a.setSummary(row.path("summary").asText());
        a.setPublishedAt(OffsetDateTime.parse(row.path("published_at").asText()));
        // The caller attaches verified frozen bodies when the replay includes source context.
        return a;
    }

    static class RecordedClient extends OpenAiClient {
        final ObjectMapper json; final Path cache; final ArrayNode requests;
        int newCalls, cachedCalls; double newCost;
        RecordedClient(OpenAiProperties properties, ObjectMapper json, Path cache) {
            super(properties, json); this.json = json; this.cache = cache; requests = json.createArrayNode();
        }
        @Override public LlmResult complete(String model, String instructions, String input, String reasoning,
                String verbosity, String name, JsonNode schema, int max) {
            try {
                var request = json.createObjectNode().put("model", model).put("instructions", instructions).put("input", input)
                        .put("reasoning", reasoning).put("verbosity", verbosity).put("schemaName", name).put("maxOutputTokens", max);
                request.set("schema", schema);
                Path file = cache.resolve(name + "-" + ObservationStore.bodyHash(request.toString()) + ".json");
                boolean hit = Files.exists(file); LlmResult result;
                if (hit) { result = json.treeToValue(json.readTree(file.toFile()).path("response"), LlmResult.class); cachedCalls++; }
                else {
                    result = super.complete(model, instructions, input, reasoning, verbosity, name, schema, max); newCalls++;
                    double rate = model.contains("terra") ? 2 : .2; var u = result.usage();
                    newCost += ((u.inputTokens() - u.cachedInputTokens()) * rate + u.cachedInputTokens() * rate / 10
                            + u.outputTokens() * rate * 6) / 1_000_000;
                    var saved = json.createObjectNode(); saved.set("request", request); saved.set("response", json.valueToTree(result));
                    json.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), saved);
                }
                requests.addObject().put("stage", name).put("cached", hit).put("cacheFile", file.toString())
                        .set("usage", json.valueToTree(result.usage()));
                return result; // Keep logical usage on a cache hit: production guards must still run.
            } catch (Exception e) {
                Throwable root = e; while (root.getCause() != null) root = root.getCause();
                throw new IllegalStateException("Recorded " + name + " failed: " + root.getClass().getSimpleName() + ": " + root.getMessage(), e);
            }
        }
        @Override public EmbeddingResult embed(List<String> inputs) {
            try {
                Path file = cache.resolve("embedding-" + ObservationStore.bodyHash(json.writeValueAsString(inputs)) + ".json");
                if (Files.exists(file)) { cachedCalls++; return json.readValue(file.toFile(), EmbeddingResult.class); }
                var result = super.embed(inputs); newCalls++; newCost += result.inputTokens() * .13 / 1_000_000;
                json.writeValue(file.toFile(), result); return result;
            } catch (Exception e) { throw new IllegalStateException("Recorded embedding failed", e); }
        }
    }
}
