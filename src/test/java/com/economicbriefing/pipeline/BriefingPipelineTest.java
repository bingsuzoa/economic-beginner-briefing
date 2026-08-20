package com.economicbriefing.pipeline;

import java.time.LocalDate;

import com.economicbriefing.admin.entity.PipelineRunEntity;
import com.economicbriefing.admin.repository.PipelineItemRepository;
import com.economicbriefing.admin.repository.PipelineLogRepository;
import com.economicbriefing.admin.repository.PipelineRunRepository;
import com.economicbriefing.classifier.repository.ArticleAnalysisRepository;
import com.economicbriefing.classifier.repository.ArticleAnalyzerResultRepository;
import com.economicbriefing.domain.execution.ExecutionLog;
import com.economicbriefing.domain.execution.ExecutionStatus;
import com.economicbriefing.domain.execution.PublicationDecision;
import com.economicbriefing.util.KstDateTimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class BriefingPipelineTest {

    @Autowired private BriefingPipeline pipeline;
    @Autowired private ExecutionTracker executionTracker;
    @Autowired private PipelineRunRepository runRepository;
    @Autowired private PipelineLogRepository logRepository;
    @Autowired private PipelineItemRepository itemRepository;
    @Autowired private ArticleAnalysisRepository analysisRepository;
    @Autowired private ArticleAnalyzerResultRepository analyzerResultRepository;

    @BeforeEach
    void setUp() {
        analyzerResultRepository.deleteAll();
        analysisRepository.deleteAll();
        logRepository.deleteAll();
        itemRepository.deleteAll();
        runRepository.deleteAll();
    }

    @Test
    void shouldRunFullPipelineWithMockImplementations() {
        PipelineOptions options = PipelineOptions.manual(LocalDate.of(2025, 1, 15));

        ExecutionLog log = pipeline.run(options);

        assertNotNull(log);
        assertEquals(ExecutionStatus.SUCCESS, log.getStatus());
        assertEquals(LocalDate.of(2025, 1, 15), log.getTargetDate());
        assertTrue(log.getCollectedArticleCount() > 0);
        assertTrue(log.getSelectedNewsCount() > 0);
        assertNotNull(log.getCompletedAt());
    }

    @Test
    void shouldSaveArticleAnalyzerResultsWithFinalAnalyses() {
        PipelineOptions options = PipelineOptions.manual(LocalDate.of(2025, 1, 20));

        ExecutionLog log = pipeline.run(options);

        assertEquals(ExecutionStatus.SUCCESS, log.getStatus());
        var analyzerResults = analyzerResultRepository.findAll();
        var finalResults = analysisRepository.findAll();
        assertFalse(analyzerResults.isEmpty());
        assertFalse(finalResults.isEmpty());

        var analyzerResult = analyzerResults.get(0);
        assertNotNull(analyzerResult.getArticleId());
        assertNotNull(analyzerResult.getBriefingId());
        assertEquals("mock", analyzerResult.getModelName());
        assertEquals("mock-article-analyzer-v1", analyzerResult.getAnalyzerPromptVersion());
        assertTrue(analyzerResult.getAnalysisJson().contains("\"issues\""));
        assertTrue(analyzerResult.getAnalysisJson().contains("\"mainFacts\""));
        assertTrue(analyzerResult.getAnalysisJson().contains("\"changes\""));
        assertTrue(analyzerResult.getAnalysisJson().contains("\"relations\""));
        assertTrue(analyzerResult.getAnalysisJson().contains("\"statements\""));
        assertTrue(analyzerResult.getAnalysisJson().contains("\"keyTerms\""));
        assertTrue(finalResults.stream().anyMatch(finalResult ->
                finalResult.getArticleId().equals(analyzerResult.getArticleId())
                        && finalResult.getBriefingId().equals(analyzerResult.getBriefingId())));
    }

    @Test
    void shouldKeepAnalyzerResultHistoryForRepeatedAnalysisAfterFailure() {
        LocalDate date = LocalDate.of(2025, 1, 21);
        pipeline.run(PipelineOptions.manual(date));
        long firstCount = analyzerResultRepository.count();

        ExecutionLog failed = new ExecutionLog("exec-analyzer-history", date, KstDateTimeUtil.now());
        failed.markFailed(KstDateTimeUtil.now());
        executionTracker.startRun("exec-analyzer-history", "2025-01-21", "MANUAL", failed.getStartedAt());
        executionTracker.finishRun("exec-analyzer-history", "2025-01-21", failed);

        pipeline.run(PipelineOptions.manual(date));

        assertTrue(analyzerResultRepository.count() > firstCount);
    }

    @Test
    void shouldSkipDuplicateExecution() {
        PipelineOptions options = PipelineOptions.manual(LocalDate.of(2025, 1, 15));

        ExecutionLog firstLog = pipeline.run(options);
        assertEquals(ExecutionStatus.SUCCESS, firstLog.getStatus());

        ExecutionLog secondLog = pipeline.run(options);
        assertEquals(ExecutionStatus.SUCCESS, secondLog.getStatus());
        assertEquals(0, secondLog.getCollectedArticleCount());
    }

    // Run history has to survive a restart, so it must live in the database, not in memory.
    @Test
    void shouldPersistRunToDatabase() {
        pipeline.run(PipelineOptions.manual(LocalDate.of(2025, 1, 16)));

        PipelineRunEntity run = runRepository.findFirstByDedupeKeyOrderByStartedAtDesc("2025-01-16")
                .orElseThrow(() -> new AssertionError("run was not persisted"));

        assertEquals("SUCCESS", run.getStatus());
        assertEquals("MANUAL", run.getTriggerType());
        assertNotNull(run.getFinishedAt());
        assertTrue(run.getCollectedCount() > 0);
        assertFalse(logRepository.findByRunIdOrderByCreatedAtAsc(run.getId()).isEmpty(),
                "step logs should be written");
        assertFalse(itemRepository.findByRunIdOrderByIdAsc(run.getId()).isEmpty(),
                "per-article items should be written");
    }

    @Test
    void shouldAllowRetryAfterFailure() {
        LocalDate date = LocalDate.of(2025, 1, 17);

        ExecutionLog failed = new ExecutionLog("exec-fail", date, KstDateTimeUtil.now());
        failed.markFailed(KstDateTimeUtil.now());
        executionTracker.startRun("exec-fail", "2025-01-17", "MANUAL", failed.getStartedAt());
        executionTracker.finishRun("exec-fail", "2025-01-17", failed);

        PublicationDecision decision = executionTracker.checkDuplicate("2025-01-17");
        assertEquals(PublicationDecision.RETRY_PREVIOUS_FAILURE, decision);

        ExecutionLog retryLog = pipeline.run(PipelineOptions.manual(date));
        assertEquals(ExecutionStatus.SUCCESS, retryLog.getStatus());
    }

    // The old in-memory tracker keyed writes by target date but read by date+hour,
    // so scheduled runs never detected their own duplicates.
    @Test
    void shouldDedupeHourlyRunsByDateAndHour() {
        ExecutionLog log = new ExecutionLog("exec-hourly", LocalDate.of(2025, 1, 19), KstDateTimeUtil.now());
        log.markSuccess(KstDateTimeUtil.now());
        executionTracker.startRun("exec-hourly", "2025-01-19T09", "SCHEDULER", log.getStartedAt());
        executionTracker.finishRun("exec-hourly", "2025-01-19T09", log);

        assertEquals(PublicationDecision.SKIP_ALREADY_PUBLISHED,
                executionTracker.checkDuplicate("2025-01-19T09"));
        assertEquals(PublicationDecision.PUBLISH,
                executionTracker.checkDuplicate("2025-01-19T10"),
                "a different hour is a different run");
    }

    @Test
    void shouldSetExecutionMetadata() {
        PipelineOptions options = PipelineOptions.manual(LocalDate.of(2025, 1, 18));

        ExecutionLog log = pipeline.run(options);

        assertNotNull(log.getExecutionId());
        assertNotNull(log.getStartedAt());
        assertNotNull(log.getCompletedAt());
        assertTrue(log.getCompletedAt().isAfter(log.getStartedAt())
                || log.getCompletedAt().isEqual(log.getStartedAt()));
    }
}
