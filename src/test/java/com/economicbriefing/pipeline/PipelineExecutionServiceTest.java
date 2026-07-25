package com.economicbriefing.pipeline;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.economicbriefing.domain.execution.ExecutionLog;
import com.economicbriefing.util.KstDateTimeUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PipelineExecutionServiceTest {

    private final PipelineLock lock = new InMemoryPipelineLock();

    private static ExecutionLog successLog() {
        ExecutionLog log = new ExecutionLog("exec-1", LocalDate.of(2026, 7, 25), KstDateTimeUtil.now());
        log.markSuccess(KstDateTimeUtil.now());
        return log;
    }

    @Test
    void shouldRunAndReleaseTheLock() {
        BriefingPipeline pipeline = mock(BriefingPipeline.class);
        when(pipeline.run(any())).thenReturn(successLog());
        var service = new PipelineExecutionService(pipeline, lock);

        Optional<ExecutionLog> result = service.tryRun(PipelineOptions.hourly());

        assertTrue(result.isPresent());
        assertFalse(service.isRunning(), "lock must be released after a run");
    }

    /** The core guarantee: a second trigger while one is in flight must not start a run. */
    @Test
    void shouldSkipWhenAnotherRunIsInFlight() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger starts = new AtomicInteger();

        BriefingPipeline pipeline = mock(BriefingPipeline.class);
        when(pipeline.run(any())).thenAnswer(invocation -> {
            starts.incrementAndGet();
            running.countDown();
            release.await(5, TimeUnit.SECONDS);
            return successLog();
        });

        var service = new PipelineExecutionService(pipeline, lock);

        Thread first = new Thread(() -> service.tryRun(PipelineOptions.hourly()));
        first.start();
        assertTrue(running.await(5, TimeUnit.SECONDS), "first run should have started");

        // second trigger arrives mid-flight
        Optional<ExecutionLog> second = service.tryRun(PipelineOptions.hourly());
        assertTrue(second.isEmpty(), "concurrent run must be skipped");
        assertTrue(service.isRunning());

        release.countDown();
        first.join(5000);

        assertEquals(1, starts.get(), "pipeline must have been entered exactly once");
        assertFalse(service.isRunning(), "lock released once the first run finished");
    }

    @Test
    void shouldRejectAsyncTriggerWhileRunning() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        BriefingPipeline pipeline = mock(BriefingPipeline.class);
        when(pipeline.run(any())).thenAnswer(invocation -> {
            running.countDown();
            release.await(5, TimeUnit.SECONDS);
            return successLog();
        });

        var service = new PipelineExecutionService(pipeline, lock);

        assertTrue(service.tryRunAsync(PipelineOptions.manual(LocalDate.of(2026, 7, 25))));
        assertTrue(running.await(5, TimeUnit.SECONDS));

        assertFalse(service.tryRunAsync(PipelineOptions.manual(LocalDate.of(2026, 7, 25))),
                "admin API should get a 409 while a run is in flight");

        release.countDown();
    }

    /** A failing run must not strand the lock, or every later tick would skip forever. */
    @Test
    void shouldReleaseTheLockWhenThePipelineThrows() {
        BriefingPipeline pipeline = mock(BriefingPipeline.class);
        when(pipeline.run(any())).thenThrow(new IllegalStateException("boom"));
        var service = new PipelineExecutionService(pipeline, lock);

        Optional<ExecutionLog> result = service.tryRun(PipelineOptions.hourly());

        assertTrue(result.isEmpty(), "failure is reported as an empty result, not an exception");
        assertFalse(service.isRunning(), "lock must be released after a failure");

        // and the next trigger still runs
        reset(pipeline);
        when(pipeline.run(any())).thenReturn(successLog());
        assertTrue(service.tryRun(PipelineOptions.hourly()).isPresent());
    }

    @Test
    void shouldLabelSchedulerAndAdminRunsDifferently() {
        assertEquals("Scheduler", PipelineExecutionService.sourceOf(PipelineOptions.hourly()));
        assertEquals("Admin",
                PipelineExecutionService.sourceOf(PipelineOptions.manual(LocalDate.of(2026, 7, 25))));
    }
}
