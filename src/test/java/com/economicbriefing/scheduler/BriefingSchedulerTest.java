package com.economicbriefing.scheduler;

import com.economicbriefing.pipeline.PipelineExecutionService;
import com.economicbriefing.pipeline.PipelineOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BriefingSchedulerTest {

    @Mock
    private PipelineExecutionService executionService;

    @InjectMocks
    private BriefingScheduler scheduler;

    @Test
    void shouldRunHourlyBriefing() {
        when(executionService.tryRun(any(PipelineOptions.class))).thenReturn(Optional.empty());

        scheduler.runHourlyBriefing();

        ArgumentCaptor<PipelineOptions> captor = ArgumentCaptor.forClass(PipelineOptions.class);
        verify(executionService).tryRun(captor.capture());

        PipelineOptions options = captor.getValue();
        assertEquals("SCHEDULER", options.triggerType());
        assertNotNull(options.timeRange());
    }

    /** Requirement 7: a failing tick must not propagate, or the trigger stops firing. */
    @Test
    void shouldHandleExceptionGracefully() {
        when(executionService.tryRun(any())).thenThrow(new RuntimeException("Unexpected error"));

        assertDoesNotThrow(() -> scheduler.runHourlyBriefing());
        assertDoesNotThrow(() -> scheduler.runHourlyBriefing());
        verify(executionService, times(2)).tryRun(any());
    }

    @Test
    void shouldCallExecutionServiceExactlyOnce() {
        when(executionService.tryRun(any())).thenReturn(Optional.empty());

        scheduler.runHourlyBriefing();

        verify(executionService, times(1)).tryRun(any());
    }

    /** The scheduler decides when; it must not re-implement how a run happens. */
    @Test
    void shouldDelegateEverythingToTheSharedService() {
        when(executionService.tryRun(any())).thenReturn(Optional.empty());

        scheduler.runHourlyBriefing();

        verify(executionService).tryRun(any());
        verifyNoMoreInteractions(executionService);
    }

    @Test
    void shouldSeparateScheduledAndManualTriggerTypes() {
        assertEquals("MANUAL", PipelineOptions.manual(LocalDate.of(2026, 7, 25)).triggerType());
        assertEquals("SCHEDULER", PipelineOptions.hourly().triggerType());
    }
}
