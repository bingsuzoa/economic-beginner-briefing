package com.economicbriefing.e2e;

import java.time.LocalDate;

import com.economicbriefing.admin.repository.PipelineItemRepository;
import com.economicbriefing.admin.repository.PipelineLogRepository;
import com.economicbriefing.admin.repository.PipelineRunRepository;
import com.economicbriefing.classifier.repository.ArticleAnalysisRepository;
import com.economicbriefing.classifier.repository.ArticleAnalyzerResultRepository;
import com.economicbriefing.classifier.repository.ArticleRouterResultRepository;
import com.economicbriefing.analyzer.openai.prompt.RetrievalRouterPromptBuilder;
import com.economicbriefing.domain.execution.ExecutionLog;
import com.economicbriefing.domain.execution.ExecutionStatus;
import com.economicbriefing.pipeline.BriefingPipeline;
import com.economicbriefing.pipeline.PipelineOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test with DRY_RUN=true.
 * Runs the full pipeline: MockCollector → filters → MockAnalyzer → article_analyses.
 */
@SpringBootTest
@ActiveProfiles("test")
class DryRunE2ETest {

    @Autowired private BriefingPipeline pipeline;
    @Autowired private PipelineRunRepository runRepository;
    @Autowired private PipelineLogRepository logRepository;
    @Autowired private PipelineItemRepository itemRepository;
    @Autowired private ArticleAnalysisRepository analysisRepository;
    @Autowired private ArticleAnalyzerResultRepository analyzerResultRepository;
    @Autowired private ArticleRouterResultRepository routerResultRepository;

    @BeforeEach
    void setUp() {
        routerResultRepository.deleteAll();
        analyzerResultRepository.deleteAll();
        logRepository.deleteAll();
        itemRepository.deleteAll();
        runRepository.deleteAll();
        analysisRepository.deleteAll();
    }

    @Test
    void shouldRunFullPipelineEndToEnd() {
        PipelineOptions options = PipelineOptions.manual(LocalDate.of(2025, 3, 15));

        ExecutionLog log = pipeline.run(options);

        assertEquals(ExecutionStatus.SUCCESS, log.getStatus());
        assertNotNull(log.getExecutionId());
        assertEquals(LocalDate.of(2025, 3, 15), log.getTargetDate());
        assertNotNull(log.getStartedAt());
        assertNotNull(log.getCompletedAt());
        assertTrue(log.getCollectedArticleCount() > 0, "Should have collected articles");
        assertTrue(log.getSelectedNewsCount() > 0, "Should have selected news");
    }

    /** article_analyses is what the public API serves, so it is the real output of a run. */
    @Test
    void shouldStoreAnalysesForTheApiToServe() {
        pipeline.run(PipelineOptions.manual(LocalDate.of(2025, 3, 16)));

        var analyses = analysisRepository.findAll();
        assertFalse(analyses.isEmpty(), "pipeline should persist analyses");

        var first = analyses.get(0);
        assertNotNull(first.getArticleId());
        assertNotNull(first.getBriefingId());
        assertNotNull(first.getAnalysisJson());
        assertTrue(first.getAnalysisJson().contains("easyTitle"));

        var analyzerResults = analyzerResultRepository.findAll();
        assertFalse(analyzerResults.isEmpty(), "pipeline should persist article analyzer results");
        var analyzerResult = analyzerResults.get(0);
        assertNotNull(analyzerResult.getArticleId());
        assertEquals(first.getBriefingId(), analyzerResult.getBriefingId());
        assertEquals("mock", analyzerResult.getModelName());
        assertEquals("mock-article-analyzer-v1", analyzerResult.getPromptVersion());
        assertTrue(analyzerResult.getAnalysisJson().contains("\"issues\""));
        assertTrue(analyzerResult.getAnalysisJson().contains("\"mainFacts\""));
        assertTrue(analyzerResult.getAnalysisJson().contains("\"changes\""));
        assertTrue(analyzerResult.getAnalysisJson().contains("\"relations\""));
        assertTrue(analyzerResult.getAnalysisJson().contains("\"statements\""));
        assertTrue(analyzerResult.getAnalysisJson().contains("\"keyTerms\""));

        var routerResults = routerResultRepository.findAll();
        assertFalse(routerResults.isEmpty(), "pipeline should persist retrieval router results");
        var routerResult = routerResults.get(0);
        assertEquals(analyzerResult.getArticleId(), routerResult.getArticleId());
        assertEquals(first.getBriefingId(), routerResult.getBriefingId());
        assertEquals("mock", routerResult.getModelName());
        assertEquals(RetrievalRouterPromptBuilder.PROMPT_VERSION, routerResult.getPromptVersion());
        assertTrue(routerResult.getRouterJson().contains("\"needsRetrieval\""));

        long firstRunCount = analyzerResultRepository.count();
        long firstRouterRunCount = routerResultRepository.count();
        pipeline.run(PipelineOptions.manual(LocalDate.of(2025, 3, 21)));
        assertTrue(analyzerResultRepository.count() > firstRunCount,
                "reanalyzing the same articles should preserve history");
        assertTrue(routerResultRepository.count() > firstRouterRunCount,
                "rerouting the same articles should preserve history");
    }

    @Test
    void shouldRecordExecutionAfterRun() {
        pipeline.run(PipelineOptions.manual(LocalDate.of(2025, 3, 17)));

        var recorded = runRepository.findFirstByDedupeKeyOrderByStartedAtDesc("2025-03-17");
        assertTrue(recorded.isPresent(), "Execution should be recorded in tracker");
        assertEquals("SUCCESS", recorded.get().getStatus());
    }

    @Test
    void shouldSkipDuplicateExecution() {
        LocalDate date = LocalDate.of(2025, 3, 18);

        ExecutionLog first = pipeline.run(PipelineOptions.manual(date));
        assertEquals(ExecutionStatus.SUCCESS, first.getStatus());
        assertTrue(first.getCollectedArticleCount() > 0);

        ExecutionLog second = pipeline.run(PipelineOptions.manual(date));
        assertEquals(ExecutionStatus.SUCCESS, second.getStatus());
        assertEquals(0, second.getCollectedArticleCount(),
                "Duplicate run should skip collection");
    }

    @Test
    void shouldHandleHourlyMode() {
        ExecutionLog log = pipeline.run(PipelineOptions.hourly());

        assertNotNull(log);
        // Hourly mode may collect 0 articles if mock doesn't match the time range,
        // but should not throw an exception
        assertTrue(log.getStatus() == ExecutionStatus.SUCCESS
                || log.getStatus() == ExecutionStatus.PARTIAL_SUCCESS);
    }

    @Test
    void shouldHaveNoErrorsOnCleanRun() {
        ExecutionLog log = pipeline.run(PipelineOptions.manual(LocalDate.of(2025, 3, 20)));

        assertTrue(log.getErrors().isEmpty(),
                "Clean E2E run should have no errors, but had: " + log.getErrors());
    }
}
