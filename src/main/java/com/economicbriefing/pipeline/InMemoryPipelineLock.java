package com.economicbriefing.pipeline;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "briefing.dry-run", havingValue = "true", matchIfMissing = true)
public class InMemoryPipelineLock implements PipelineLock {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPipelineLock.class);

    private final AtomicReference<String> currentRunId = new AtomicReference<>(null);

    @Override
    public boolean acquire(String runId, String triggerType) {
        boolean acquired = currentRunId.compareAndSet(null, runId);
        if (acquired) {
            log.info("Pipeline lock acquired: runId={}, trigger={}", runId, triggerType);
        } else {
            log.warn("Pipeline lock denied: runId={}, currentRunId={}", runId, currentRunId.get());
        }
        return acquired;
    }

    @Override
    public void release(String runId) {
        boolean released = currentRunId.compareAndSet(runId, null);
        if (released) {
            log.info("Pipeline lock released: runId={}", runId);
        }
    }

    @Override
    public boolean isLocked() {
        return currentRunId.get() != null;
    }

    @Override
    public String getCurrentRunId() {
        return currentRunId.get();
    }
}
