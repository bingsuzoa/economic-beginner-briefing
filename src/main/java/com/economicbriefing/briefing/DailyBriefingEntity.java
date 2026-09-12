package com.economicbriefing.briefing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "daily_briefings")
public class DailyBriefingEntity {
    @Id private String id;
    @Column(name = "target_date", nullable = false) private LocalDate targetDate;
    @Column(nullable = false) private int revision;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "trigger_type", nullable = false, length = 16) private String triggerType;
    @Column(name = "pipeline_version", nullable = false, length = 32) private String pipelineVersion;
    @Column(name = "models_json", nullable = false, columnDefinition = "TEXT") private String modelsJson = "{}";
    @Column(name = "usage_json", nullable = false, columnDefinition = "TEXT") private String usageJson = "{}";
    @Column(name = "trace_json", nullable = false, columnDefinition = "TEXT") private String traceJson = "{}";
    @Column(name = "result_json", columnDefinition = "TEXT") private String resultJson;
    @Column(name = "input_hash", length = 64) private String inputHash;
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;
    @Column(name = "started_at", nullable = false) private OffsetDateTime startedAt;
    @Column(name = "finished_at") private OffsetDateTime finishedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }
    public int getRevision() { return revision; }
    public void setRevision(int revision) { this.revision = revision; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getPipelineVersion() { return pipelineVersion; }
    public void setPipelineVersion(String pipelineVersion) { this.pipelineVersion = pipelineVersion; }
    public String getModelsJson() { return modelsJson; }
    public void setModelsJson(String modelsJson) { this.modelsJson = modelsJson; }
    public String getUsageJson() { return usageJson; }
    public void setUsageJson(String usageJson) { this.usageJson = usageJson; }
    public String getTraceJson() { return traceJson; }
    public void setTraceJson(String traceJson) { this.traceJson = traceJson; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getInputHash() { return inputHash; }
    public void setInputHash(String inputHash) { this.inputHash = inputHash; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
}
