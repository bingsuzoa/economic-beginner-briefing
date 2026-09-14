package com.economicbriefing.briefing;

import com.economicbriefing.article.ArticleEntity;
import com.economicbriefing.article.ParagraphSplitter;
import com.economicbriefing.config.OpenAiProperties;
import com.economicbriefing.llm.OpenAiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit paid evaluation of frozen title/summary candidates; no database or published result writes. */
@EnabledIfEnvironmentVariable(named = "DAILY_SELECTION_FIXTURE", matches = ".+")
class DailySelectionReplayTest {
    @Test void replaySelection() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        var fixture = json.readTree(Path.of(System.getenv("DAILY_SELECTION_FIXTURE")).toFile());
        Path output = Path.of(System.getenv("DAILY_SELECTION_OUTPUT"));
        assertFalse(Files.exists(output), "Preserve previous evaluation results");
        var properties = new OpenAiProperties(System.getenv("OPENAI_API_KEY"), Duration.ofSeconds(120),
                "gpt-5.6-luna", "gpt-5.6-luna", "gpt-5.6-terra", "gpt-5.6-luna", "text-embedding-3-large");
        var llm = new EconomicFlowLlm(new OpenAiClient(properties, json), properties, json, new ParagraphSplitter());
        var results = json.createArrayNode();
        var failures = new ArrayList<String>();
        try {
            for (var sample : fixture) {
                LocalDate date = LocalDate.parse(sample.path("date").asText());
                var articles = new ArrayList<ArticleEntity>();
                for (var row : sample.path("articles")) {
                    var article = new ArticleEntity();
                    article.setId(row.path("id").asText());
                    article.setTitle(row.path("title").asText());
                    article.setSummary(row.path("summary").asText());
                    article.setPublishedAt(OffsetDateTime.parse(row.path("published_at").asText()));
                    articles.add(article);
                }
                var call = llm.select(date, articles);
                var daily = call.value().briefingArticles().stream().map(a -> a.article().getId()).toList();
                var result = results.addObject().put("date", date.toString()).put("candidateCount", articles.size());
                result.set("selected", json.valueToTree(daily));
                result.set("selection", call.raw());
                result.set("usage", json.valueToTree(call.usage()));
                for (var id : sample.path("include")) if (!daily.contains(id.asText())) failures.add(date + ": missing " + id.asText());
                for (var id : sample.path("exclude")) if (daily.contains(id.asText())) failures.add(date + ": unwanted " + id.asText());
                assertTrue(call.value().all().size() <= 20);
            }
        } finally {
            json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), results);
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }
}
