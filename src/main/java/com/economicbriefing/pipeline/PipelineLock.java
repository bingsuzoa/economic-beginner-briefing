package com.economicbriefing.pipeline;

public interface PipelineLock {

    boolean acquire(String runId, String triggerType);

    void release(String runId);

    boolean isLocked();

    String getCurrentRunId();
}
