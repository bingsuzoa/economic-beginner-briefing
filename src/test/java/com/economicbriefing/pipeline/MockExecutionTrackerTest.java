package com.economicbriefing.pipeline;

import java.time.LocalDate;

import com.economicbriefing.domain.execution.ExecutionLog;
import com.economicbriefing.domain.execution.ExecutionStatus;
import com.economicbriefing.domain.execution.PublicationDecision;
import com.economicbriefing.util.KstDateTimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockExecutionTrackerTest {

    private MockExecutionTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new MockExecutionTracker();
    }

    @Test
    void shouldReturnPublishForNewDate() {
        assertEquals(PublicationDecision.PUBLISH, tracker.checkDuplicate("2025-01-15"));
    }

    @Test
    void shouldReturnSkipForSuccessfulExecution() {
        ExecutionLog log = new ExecutionLog("exec-1", LocalDate.of(2025, 1, 15), KstDateTimeUtil.now());
        log.markSuccess(KstDateTimeUtil.now());
        tracker.recordExecution(log);

        assertEquals(PublicationDecision.SKIP_ALREADY_PUBLISHED, tracker.checkDuplicate("2025-01-15"));
    }

    @Test
    void shouldReturnRetryForFailedExecution() {
        ExecutionLog log = new ExecutionLog("exec-1", LocalDate.of(2025, 1, 15), KstDateTimeUtil.now());
        log.markFailed(KstDateTimeUtil.now());
        tracker.recordExecution(log);

        assertEquals(PublicationDecision.RETRY_PREVIOUS_FAILURE, tracker.checkDuplicate("2025-01-15"));
    }

    @Test
    void shouldGetLastExecution() {
        ExecutionLog log = new ExecutionLog("exec-1", LocalDate.of(2025, 1, 15), KstDateTimeUtil.now());
        log.markSuccess(KstDateTimeUtil.now());
        tracker.recordExecution(log);

        ExecutionLog last = tracker.getLastExecution("2025-01-15");
        assertNotNull(last);
        assertEquals("exec-1", last.getExecutionId());
    }

    @Test
    void shouldReturnNullForNoExecution() {
        assertNull(tracker.getLastExecution("2025-01-15"));
    }

    @Test
    void shouldClearExecutions() {
        ExecutionLog log = new ExecutionLog("exec-1", LocalDate.of(2025, 1, 15), KstDateTimeUtil.now());
        log.markSuccess(KstDateTimeUtil.now());
        tracker.recordExecution(log);

        tracker.clear();
        assertEquals(PublicationDecision.PUBLISH, tracker.checkDuplicate("2025-01-15"));
    }
}
