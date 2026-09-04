package com.economicbriefing.analyzer.openai;

import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.economicflow.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

/** Replays only the Presenter from an already successful Analyzer/Router/Retriever debug fixture. */
@SpringBootTest
@TestPropertySource(properties = {"briefing.dry-run=false", "briefing.scheduler.enabled=false"})
@EnabledIfEnvironmentVariable(named = "ARTICLE_PRESENTER_FIXTURE_LIVE_TEST", matches = "true")
class ArticlePresenterFixtureLiveTest {
    private static final Path FIXTURE = Path.of("pipeline-debug/article-principle-retrieval-AKR20260903152051008.json");
    @Autowired ObjectMapper json;
    @Autowired ArticlePresenter presenter;

    @Test
    void replaysOnlyPresenterAgainstSavedSuccessfulInputs() throws Exception {
        var root = json.readTree(Files.readString(FIXTURE));
        var analysis = json.treeToValue(root.path("analyzer"), ArticleAnalysisResponse.class);
        var principles = json.treeToValue(root.path("principleRetrieval"), EconomicPrincipleRetriever.Context.class);
        var claims = json.convertValue(root.path("economicFlow").path("claims"),
                new TypeReference<List<List<FlowClaimCandidate>>>() {}).getFirst();
        String articleId = analysis.articles().getFirst().articleId();
        var flows = List.of(new ArticleEconomicFlow(new ArticleContext(articleId, articleId, "", OffsetDateTime.now(), ""),
                new EconomicFlowExtraction(claims)));

        var run = presenter.presentDetailed(analysis, flows, principles);
        var report = new LinkedHashMap<String, Object>();
        report.put("fixture", FIXTURE.toAbsolutePath().toString());
        report.put("presenterInput", run.input()); report.put("presenterPrompt", run.prompt());
        report.put("presenterRawOutput", json.readTree(run.raw())); report.put("parsed", run.parsed());
        report.put("validation", "PASS"); report.put("presentation", run.presentations());
        Path output = Path.of("pipeline-debug/article-presenter-fixture-" + articleId + ".json");
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        System.out.println("[ARTICLE PRESENTER FIXTURE LIVE] " + output.toAbsolutePath());
        assertEquals(articleId, run.presentations().getFirst().articleId());
    }
}
