package com.economicbriefing.admin.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "pipeline_runs")
public class PipelineRunEntity {

    @Id
    private String id;

    @Column(nullable = false, length = 20)
    private String status = "RUNNING";

    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "current_step", nullable = false, length = 20)
    private String currentStep = "INIT";

    @Column(name = "collected_count", nullable = false)
    private int collectedCount;

    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount;

    @Column(name = "analysis_success_count", nullable = false)
    private int analysisSuccessCount;

    @Column(name = "analysis_failure_count", nullable = false)
    private int analysisFailureCount;

    @Column(name = "publish_success_count", nullable = false)
    private int publishSuccessCount;

    @Column(name = "publish_failure_count", nullable = false)
    private int publishFailureCount;

    @Column(name = "total_failure_count", nullable = false)
    private int totalFailureCount;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }

    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }

    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }

    public int getCollectedCount() { return collectedCount; }
    public void setCollectedCount(int collectedCount) { this.collectedCount = collectedCount; }

    public int getDuplicateCount() { return duplicateCount; }
    public void setDuplicateCount(int duplicateCount) { this.duplicateCount = duplicateCount; }

    public int getAnalysisSuccessCount() { return analysisSuccessCount; }
    public void setAnalysisSuccessCount(int analysisSuccessCount) { this.analysisSuccessCount = analysisSuccessCount; }

    public int getAnalysisFailureCount() { return analysisFailureCount; }
    public void setAnalysisFailureCount(int analysisFailureCount) { this.analysisFailureCount = analysisFailureCount; }

    public int getPublishSuccessCount() { return publishSuccessCount; }
    public void setPublishSuccessCount(int publishSuccessCount) { this.publishSuccessCount = publishSuccessCount; }

    public int getPublishFailureCount() { return publishFailureCount; }
    public void setPublishFailureCount(int publishFailureCount) { this.publishFailureCount = publishFailureCount; }

    public int getTotalFailureCount() { return totalFailureCount; }
    public void setTotalFailureCount(int totalFailureCount) { this.totalFailureCount = totalFailureCount; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
