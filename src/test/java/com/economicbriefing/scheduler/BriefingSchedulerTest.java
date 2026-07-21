package com.economicbriefing.scheduler;

import com.economicbriefing.domain.execution.ExecutionLog;
import com.economicbriefing.domain.execution.ExecutionStatus;
import com.economicbriefing.pipeline.BriefingPipeline;
import com.economicbriefing.pipeline.PipelineOptions;
import com.economicbriefing.util.KstDateTimeUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BriefingSchedulerTest {

    @Mock
    private BriefingPipeline pipeline;

    @InjectMocks
    private BriefingScheduler scheduler;

    @Test
    void shouldRunHourlyBriefing() {
        ExecutionLog log = new ExecutionLog("test-id", LocalDate.now(), KstDateTimeUtil.now());
        log.markSuccess(KstDateTimeUtil.now());

        when(pipeline.run(any(PipelineOptions.class))).thenReturn(log);

        scheduler.runHourlyBriefing();

        ArgumentCaptor<PipelineOptions> captor = ArgumentCaptor.forClass(PipelineOptions.class);
        verify(pipeline).run(captor.capture());

        PipelineOptions options = captor.getValue();
        assertEquals("SCHEDULER", options.triggerType());
        assertNotNull(options.timeRange());
    }

    @Test
    void shouldHandleExceptionGracefully() {
        when(pipeline.run(any())).thenThrow(new RuntimeException("Unexpected error"));

        assertDoesNotThrow(() -> scheduler.runHourlyBriefing());
    }

    @Test
    void shouldCallPipelineExactlyOnce() {
        ExecutionLog log = new ExecutionLog("test-id", LocalDate.now(), KstDateTimeUtil.now());
        log.markSuccess(KstDateTimeUtil.now());
        when(pipeline.run(any())).thenReturn(log);

        scheduler.runHourlyBriefing();

        verify(pipeline, times(1)).run(any());
    }
}
