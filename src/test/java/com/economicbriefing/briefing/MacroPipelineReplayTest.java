package com.economicbriefing.briefing;

import com.economicbriefing.article.*;
import com.economicbriefing.config.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real production pipeline in an isolated corpus. Late source recovery is disclosed, never silently fetched. */
@EnabledIfEnvironmentVariable(named = "MACRO_PIPELINE_DB", matches = "economic_briefing_review_macro_[a-z0-9_]+")
class MacroPipelineReplayTest {
    /** Focused source-stage diagnosis before paying for a new full-pipeline attempt. */
    @Test
    @EnabledIfEnvironmentVariable(named = "MACRO_EXTRACTION_IDS", matches = ".+")
    void extractFrozenSources() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        Path base = Path.of(System.getenv("MACRO_PIPELINE_ROOT"));
        Path output = base.resolve(System.getenv("MACRO_PIPELINE_ATTEMPT"));
        Files.createDirectory(output);
        var properties = new OpenAiProperties(System.getenv("OPENAI_API_KEY"), Duration.ofSeconds(240),
                "gpt-5.6-luna", "gpt-5.6-luna", "gpt-5.6-terra", "gpt-5.6-luna", "text-embedding-3-large");
        var client = new MacroSelectionReplayTest.RecordedClient(properties, json, base.resolve("request-cache"));
        var splitter = new ParagraphSplitter();
        var llm = new EconomicFlowLlm(client, properties, json, splitter);
        var pending = new HashSet<>(List.of(System.getenv("MACRO_EXTRACTION_IDS").split(",")));
        for (var source : json.readTree(base.resolve("frozen-sources.json").toFile())) {
            String id = source.path("id").asText();
            if (!pending.remove(id)) continue;
            var call = llm.extract(splitter.split(source.path("body").asText()));
            var result = json.createObjectNode().put("articleId", id)
                    .put("promptVersion", EconomicFlowLlm.EXTRACTION_PROMPT_VERSION);
            result.set("observations", json.valueToTree(call.value()));
            result.set("usage", json.valueToTree(call.usage()));
            json.writerWithDefaultPrettyPrinter().writeValue(output.resolve(id.substring(id.indexOf(':') + 1) + ".json").toFile(), result);
        }
        json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("requests.json").toFile(), client.requests);
        assertTrue(pending.isEmpty(), "Missing frozen sources: " + pending);
    }

    @Test void replay() throws Exception {
        String db = System.getenv("MACRO_PIPELINE_DB");
        if (!db.matches("economic_briefing_review_macro_[a-z0-9_]+")) throw new IllegalArgumentException("Isolated DB required");
        var json = new ObjectMapper().findAndRegisterModules();
        Path base = Path.of(System.getenv("MACRO_PIPELINE_ROOT"));
        Path output = base.resolve(System.getenv("MACRO_PIPELINE_ATTEMPT"));
        Files.createDirectory(output);
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:postgresql://localhost:5432/" + db + "?stringtype=unspecified",
                System.getenv("PGUSER"), System.getenv("PGPASSWORD")));
        var properties = new OpenAiProperties(System.getenv("OPENAI_API_KEY"), Duration.ofSeconds(240),
                "gpt-5.6-luna", "gpt-5.6-luna", "gpt-5.6-terra", "gpt-5.6-luna", "text-embedding-3-large");
        var app = new AppProperties(new AppProperties.TimeoutProperties(Duration.ofSeconds(30)),
                new AppProperties.SchedulerProperties(false, "0 5 * * * *", "0 10 5 * * *"),
                new AppProperties.BudgetProperties(50000, 100000, 15000, .14));
        var splitter = new ParagraphSplitter();
        Map<String, JsonNode> sources = new LinkedHashMap<>();
        for (var row : json.readTree(base.resolve("frozen-sources.json").toFile())) sources.put(row.path("id").asText(), row);
        var errors = new ArrayList<String>();
        for (var sample : json.readTree(base.resolve("windows.json").toFile())) {
            LocalDate date = LocalDate.parse(sample.path("date").asText());
            String only = System.getenv("MACRO_PIPELINE_DATE");
            if (only != null && !date.toString().equals(only)) continue;
            var window = new ArrayList<ArticleEntity>(); var byId = new LinkedHashMap<String, ArticleEntity>();
            for (var row : sample.path("articles")) {
                var a = MacroSelectionReplayTest.article(row);
                if (sources.containsKey(a.getId())) {
                    var source = sources.get(a.getId()); a.setBody(source.path("body").asText()); a.setBodyStatus("FULL_TEXT");
                    a.setBodyFetchedAt(OffsetDateTime.parse(source.path("capturedAt").asText()));
                    jdbc.update("UPDATE articles SET body=?,body_status='FULL_TEXT',body_fetched_at=? WHERE id=?",
                            a.getBody(), a.getBodyFetchedAt(), a.getId());
                }
                window.add(a); byId.put(a.getId(), a);
                // Re-extract from identical frozen sources; matching requests retain their original usage.
                jdbc.update("DELETE FROM article_observations WHERE article_id=?", a.getId());
            }
            var articles = mock(ArticleRepository.class);
            when(articles.findByPublishedAtGreaterThanEqualAndPublishedAtLessThanOrderByPublishedAtAsc(any(), any())).thenReturn(window);
            when(articles.findById(anyString())).thenAnswer(call -> {
                String id = call.getArgument(0);
                if (byId.containsKey(id)) return Optional.of(byId.get(id));
                var rows = jdbc.query("SELECT row_to_json(a)::text FROM articles a WHERE id=?", (rs,n) -> rs.getString(1), id);
                if (rows.isEmpty()) return Optional.empty();
                var row = json.readTree(rows.getFirst()); var a = MacroSelectionReplayTest.article(row);
                a.setBody(row.path("body").asText(null)); a.setBodyStatus(row.path("body_status").asText());
                return Optional.of(a);
            });
            when(articles.save(any())).thenAnswer(call -> call.getArgument(0));
            var missing = new ArrayList<String>();
            var fetcher = mock(YonhapBodyFetcher.class);
            when(fetcher.fetchBefore(anyString(), any())).thenAnswer(call -> { String url = call.getArgument(0); missing.add(url);
                throw new IllegalStateException("No frozen source; live page fetch disabled: " + url); });
            when(fetcher.fetch(anyString())).thenAnswer(call -> { throw new IllegalStateException("Live query fetch disabled"); });
            var briefings = mock(DailyBriefingRepository.class); var last = new AtomicReference<DailyBriefingEntity>();
            when(briefings.save(any())).thenAnswer(call -> { last.set(call.getArgument(0)); return call.getArgument(0); });
            var client = new MacroSelectionReplayTest.RecordedClient(properties, json, base.resolve("request-cache"));
            var llm = new EconomicFlowLlm(client, properties, json, splitter);
            var service = new DailyBriefingService(articles, briefings, fetcher, splitter, new ObservationStore(jdbc,json,splitter),
                    new PrincipleStore(jdbc), llm, client, properties, app, json);
            try {
                var run = service.run(date, "FROZEN_MACRO_REPLAY", true).orElseThrow();
                assertEquals("SUCCESS", run.getStatus());
                var selected = json.readTree(run.getTraceJson()).path("selectedArticleIds");
                for (var id : selected) assertFalse(missing.contains(byId.get(id.asText()).getUrl()),
                        "Selected article has no frozen source: " + id.asText());
            } catch (RuntimeException | AssertionError e) { errors.add(date + ": " + e.getMessage()); }
            finally {
                var result = json.createObjectNode().put("date",date.toString()).put("mode","PIPELINE_WITH_DISCLOSED_RECOVERED_SOURCES")
                        .put("newEstimatedCostUsd",client.newCost).put("newCalls",client.newCalls).put("cachedCalls",client.cachedCalls);
                if (last.get() != null) {
                    var run = last.get(); result.put("runId",run.getId()).put("inputHash",run.getInputHash());
                    result.put("status",run.getStatus()).put("error",run.getErrorMessage());
                    result.set("usage",json.readTree(run.getUsageJson())); result.set("trace",json.readTree(run.getTraceJson()));
                    if (run.getResultJson()!=null) result.set("result",json.readTree(run.getResultJson()));
                }
                result.set("missingSources",json.valueToTree(missing)); result.set("requests",client.requests);
                json.writerWithDefaultPrettyPrinter().writeValue(output.resolve(date+".json").toFile(),result);
            }
        }
        assertTrue(errors.isEmpty(),String.join("\n",errors));
    }
}
