package com.economicbriefing.domain.execution;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExecutionLog {

    private final String executionId;
    private final LocalDate targetDate;
    private final OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private ExecutionStatus status;
    private int collectedArticleCount;
    private int selectedNewsCount;
    private final List<ExecutionError> errors = new ArrayList<>();

    public ExecutionLog(String executionId, LocalDate targetDate, OffsetDateTime startedAt) {
        this.executionId = executionId;
        this.targetDate = targetDate;
        this.startedAt = startedAt;
        this.status = ExecutionStatus.RUNNING;
    }

    public void markSuccess(OffsetDateTime completedAt) {
        this.status = ExecutionStatus.SUCCESS;
        this.completedAt = completedAt;
    }

    public void markPartialSuccess(OffsetDateTime completedAt) {
        this.status = ExecutionStatus.PARTIAL_SUCCESS;
        this.completedAt = completedAt;
    }

    public void markFailed(OffsetDateTime completedAt) {
        this.status = ExecutionStatus.FAILED;
        this.completedAt = completedAt;
    }

    public void addError(ExecutionError error) {
        this.errors.add(error);
    }

    public void setCollectedArticleCount(int count) {
        this.collectedArticleCount = count;
    }

    public void setSelectedNewsCount(int count) {
        this.selectedNewsCount = count;
    }

    public String getExecutionId() {
        return executionId;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public int getCollectedArticleCount() {
        return collectedArticleCount;
    }

    public int getSelectedNewsCount() {
        return selectedNewsCount;
    }

    public List<ExecutionError> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
