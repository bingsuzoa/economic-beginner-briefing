package com.economicbriefing.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatusResponse(
    String service,
    boolean pipelineRunning,
    String currentRunId,
    String currentStep,
    LastRunInfo lastRun,
    boolean dbConnected,
    SchedulerInfo scheduler
) {
    /** state is ENABLED / DISABLED / MISCONFIGURED — a bad cron is visible here, not just in logs. */
    public record SchedulerInfo(
        String state,
        String cron
    ) {}

    public record LastRunInfo(
        String id,
        String status,
        String startedAt,
        String finishedAt,
        String triggerType
    ) {}
}
