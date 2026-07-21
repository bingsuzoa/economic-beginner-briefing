package com.economicbriefing.pipeline;

import java.time.LocalDate;

import com.economicbriefing.domain.execution.ExecutionLog;
import com.economicbriefing.domain.execution.ExecutionStatus;
import com.economicbriefing.domain.execution.PublicationDecision;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class BriefingPipelineTest {

    @Autowired
    private BriefingPipeline pipeline;

    @Autowired
    private MockExecutionTracker executionTracker;

    @Test
    void shouldRunFullPipelineWithMockImplementations() {
        executionTracker.clear();
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
    void shouldSkipDuplicateExecution() {
        executionTracker.clear();

        // First run
        PipelineOptions options = PipelineOptions.manual(LocalDate.of(2025, 1, 15));
        ExecutionLog firstLog = pipeline.run(options);
        assertEquals(ExecutionStatus.SUCCESS, firstLog.getStatus());

        // Second run - should be skipped
        ExecutionLog secondLog = pipeline.run(options);
        assertEquals(ExecutionStatus.SUCCESS, secondLog.getStatus());
        assertEquals(0, secondLog.getCollectedArticleCount());
    }

    @Test
    void shouldRecordExecution() {
        executionTracker.clear();
        PipelineOptions options = PipelineOptions.manual(LocalDate.of(2025, 1, 16));

        pipeline.run(options);

        ExecutionLog recorded = executionTracker.getLastExecution("2025-01-16");
        assertNotNull(recorded);
        assertEquals(ExecutionStatus.SUCCESS, recorded.getStatus());
    }

    @Test
    void shouldAllowRetryAfterFailure() {
        executionTracker.clear();
        LocalDate date = LocalDate.of(2025, 1, 17);

        // Simulate a failed execution
        ExecutionLog failed = new ExecutionLog("exec-fail", date, com.economicbriefing.util.KstDateTimeUtil.now());
        failed.markFailed(com.economicbriefing.util.KstDateTimeUtil.now());
        executionTracker.recordExecution(failed);

        // Should return RETRY_PREVIOUS_FAILURE, not SKIP
        PublicationDecision decision = executionTracker.checkDuplicate("2025-01-17");
        assertEquals(PublicationDecision.RETRY_PREVIOUS_FAILURE, decision);

        // Retry should succeed
        ExecutionLog retryLog = pipeline.run(PipelineOptions.manual(date));
        assertEquals(ExecutionStatus.SUCCESS, retryLog.getStatus());
    }

    @Test
    void shouldSetExecutionMetadata() {
        executionTracker.clear();
        PipelineOptions options = PipelineOptions.manual(LocalDate.of(2025, 1, 18));

        ExecutionLog log = pipeline.run(options);

        assertNotNull(log.getExecutionId());
        assertNotNull(log.getStartedAt());
        assertNotNull(log.getCompletedAt());
        assertTrue(log.getCompletedAt().isAfter(log.getStartedAt())
                || log.getCompletedAt().isEqual(log.getStartedAt()));
    }
}
