package com.economicbriefing.e2e;

import java.time.LocalDate;

import com.economicbriefing.domain.execution.ExecutionLog;
import com.economicbriefing.domain.execution.ExecutionStatus;
import com.economicbriefing.pipeline.BriefingPipeline;
import com.economicbriefing.pipeline.MockExecutionTracker;
import com.economicbriefing.pipeline.PipelineOptions;
import com.economicbriefing.publisher.mock.MockBriefingPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test with DRY_RUN=true.
 * Runs the full pipeline: MockCollector → filters → MockAnalyzer → MockPublisher.
 */
@SpringBootTest
@ActiveProfiles("test")
class DryRunE2ETest {

    @Autowired
    private BriefingPipeline pipeline;

    @Autowired
    private MockExecutionTracker executionTracker;

    @Autowired
    private MockBriefingPublisher mockPublisher;

    @BeforeEach
    void setUp() {
        executionTracker.clear();
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

    @Test
    void shouldPublishBriefingViaMockPublisher() {
        PipelineOptions options = PipelineOptions.manual(LocalDate.of(2025, 3, 16));

        pipeline.run(options);

        assertFalse(mockPublisher.getPublishedBriefings().isEmpty(),
                "Mock publisher should have received at least one briefing");

        var briefing = mockPublisher.getPublishedBriefings().get(
                mockPublisher.getPublishedBriefings().size() - 1);
        assertNotNull(briefing.id(), "Briefing should have an ID");
        assertFalse(briefing.news().isEmpty(), "Briefing should have news items");
        assertNotNull(briefing.overallSummary(), "Briefing should have a summary");
    }

    @Test
    void shouldRecordExecutionAfterRun() {
        LocalDate date = LocalDate.of(2025, 3, 17);
        PipelineOptions options = PipelineOptions.manual(date);

        pipeline.run(options);

        ExecutionLog recorded = executionTracker.getLastExecution("2025-03-17");
        assertNotNull(recorded, "Execution should be recorded in tracker");
        assertEquals(ExecutionStatus.SUCCESS, recorded.getStatus());
    }

    @Test
    void shouldSkipDuplicateExecution() {
        LocalDate date = LocalDate.of(2025, 3, 18);

        // First run
        ExecutionLog first = pipeline.run(PipelineOptions.manual(date));
        assertEquals(ExecutionStatus.SUCCESS, first.getStatus());
        assertTrue(first.getCollectedArticleCount() > 0);

        // Second run - should be skipped (dedup)
        ExecutionLog second = pipeline.run(PipelineOptions.manual(date));
        assertEquals(ExecutionStatus.SUCCESS, second.getStatus());
        assertEquals(0, second.getCollectedArticleCount(),
                "Duplicate run should skip collection");
    }

    @Test
    void shouldHandleHourlyMode() {
        PipelineOptions options = PipelineOptions.hourly();

        ExecutionLog log = pipeline.run(options);

        assertNotNull(log);
        // Hourly mode may collect 0 articles if mock doesn't match the time range,
        // but should not throw an exception
        assertTrue(log.getStatus() == ExecutionStatus.SUCCESS
                || log.getStatus() == ExecutionStatus.PARTIAL_SUCCESS);
    }

    @Test
    void shouldProduceBriefingWithCorrectStructure() {
        PipelineOptions options = PipelineOptions.manual(LocalDate.of(2025, 3, 19));

        pipeline.run(options);

        var briefing = mockPublisher.getPublishedBriefings().get(
                mockPublisher.getPublishedBriefings().size() - 1);

        // Validate briefing structure matches Node.js output format
        assertNotNull(briefing.id());
        assertNotNull(briefing.overallSummary());
        assertFalse(briefing.overallSummary().isEmpty());
        assertFalse(briefing.news().isEmpty());

        var firstNews = briefing.news().get(0);
        assertNotNull(firstNews.id());
        assertNotNull(firstNews.representativeTitle());
        assertFalse(firstNews.sources().isEmpty());

        // Check metadata
        assertNotNull(briefing.metadata());
        assertTrue(briefing.metadata().selectedNewsCount() > 0);
    }

    @Test
    void shouldHaveNoErrorsOnCleanRun() {
        PipelineOptions options = PipelineOptions.manual(LocalDate.of(2025, 3, 20));

        ExecutionLog log = pipeline.run(options);

        assertTrue(log.getErrors().isEmpty(),
                "Clean E2E run should have no errors, but had: " + log.getErrors());
    }
}
