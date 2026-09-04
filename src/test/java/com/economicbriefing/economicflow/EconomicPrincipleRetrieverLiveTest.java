package com.economicbriefing.economicflow;

import com.economicbriefing.classifier.EmbeddingService;
import com.economicbriefing.economicflow.repository.PrincipleVectorRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {"briefing.scheduler.enabled=false", "briefing.dry-run=true"})
@EnabledIfEnvironmentVariable(named = "ECONOMIC_PRINCIPLE_RETRIEVER_LIVE_TEST", matches = "true")
class EconomicPrincipleRetrieverLiveTest {
    @Autowired private EconomicPrincipleRetriever retriever;
    @Autowired private PrincipleVectorRepository repository;
    @Autowired private EmbeddingService embeddings;
    @Autowired private ObjectMapper json;

    @Test
    void retrievesPrinciplesForWhySystemAndTerm() {
        var context = retriever.retrieve(List.of(
                new EconomicPrincipleRetriever.Query("ROUTER_WHY", "smoke.why",
                        "기준금리 인상이 왜 채권 가격 하락으로 이어지는가?"),
                new EconomicPrincipleRetriever.Query("ROUTER_SYSTEM", "smoke.system",
                        "환율은 어떤 구조와 방식으로 결정되는가?"),
                new EconomicPrincipleRetriever.Query("ROUTER_TERM", "smoke.term",
                        "국고채 금리란 무엇인가?")));

        assertEquals(3, context.queries().size());
        context.queries().forEach(result -> {
            assertFalse(result.results().isEmpty(), result.request().origin());
            assertTrue(result.results().size() <= 3);
            assertTrue(result.results().stream().allMatch(chunk -> chunk.chunkId() != null && !chunk.content().isBlank()));
        });
    }

    @Test
    void searchesBondPriceAndYieldMechanismWithTheProductionVectorPath() throws Exception {
        var profile = repository.embeddingProfile().orElseThrow();
        var queries = List.of(
                "원화 강세가 국고채 금리 하락에 왜 영향을 미치는가?",
                "채권을 사려는 수요가 늘면 왜 채권 금리가 내려가는가?",
                "채권 가격이 오르면 왜 채권 수익률은 내려가는가?",
                "채권 가격과 채권 금리는 왜 반대로 움직이는가?");
        var report = new LinkedHashMap<String, Object>();
        report.put("embeddingModel", profile.model());
        report.put("dimensions", profile.dimensions());
        report.put("results", queries.stream().map(query -> {
            var vector = embeddings.embed(query, profile.model(), profile.dimensions());
            var chunks = repository.search(EmbeddingService.toVectorString(vector), profile.model(), profile.dimensions(), 5);
            return java.util.Map.of("query", query, "top5", chunks);
        }).toList());
        Path output = Path.of("pipeline-debug/principle-bond-price-yield-search.json");
        Files.createDirectories(output.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        System.out.println("[PRINCIPLE BOND SEARCH LIVE] " + output.toAbsolutePath());
        assertEquals(4, ((List<?>) report.get("results")).size());
    }
}
