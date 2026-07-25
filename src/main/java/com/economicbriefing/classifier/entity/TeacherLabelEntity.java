package com.economicbriefing.classifier.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "teacher_labels")
public class TeacherLabelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false, length = 64)
    private String articleId;

    @Column(nullable = false, length = 20)
    private String label;

    private Double confidence;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "affected_areas", columnDefinition = "TEXT")
    private String affectedAreas; // JSON string, JSONB in Postgres / TEXT in H2

    @Column(length = 20)
    private String severity;

    @Column(name = "needs_follow_up")
    private boolean needsFollowUp;

    @Column(name = "usable_for_training")
    private boolean usableForTraining = true;

    @Column(name = "review_status", length = 20)
    private String reviewStatus = "NONE";

    @Column(name = "reviewed_by", length = 128)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "teacher_model_provider", length = 64)
    private String teacherModelProvider;

    @Column(name = "teacher_model_name", length = 128)
    private String teacherModelName;

    @Column(name = "teacher_prompt_version", length = 32)
    private String teacherPromptVersion;

    @Column(name = "teacher_temperature")
    private Double teacherTemperature;

    @Column(name = "labeled_at", nullable = false)
    private OffsetDateTime labeledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (labeledAt == null) labeledAt = now;
        if (createdAt == null) createdAt = now;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getArticleId() { return articleId; }
    public void setArticleId(String articleId) { this.articleId = articleId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getAffectedAreas() { return affectedAreas; }
    public void setAffectedAreas(String affectedAreas) { this.affectedAreas = affectedAreas; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public boolean isNeedsFollowUp() { return needsFollowUp; }
    public void setNeedsFollowUp(boolean needsFollowUp) { this.needsFollowUp = needsFollowUp; }

    public boolean isUsableForTraining() { return usableForTraining; }
    public void setUsableForTraining(boolean usableForTraining) { this.usableForTraining = usableForTraining; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getTeacherModelProvider() { return teacherModelProvider; }
    public void setTeacherModelProvider(String teacherModelProvider) { this.teacherModelProvider = teacherModelProvider; }

    public String getTeacherModelName() { return teacherModelName; }
    public void setTeacherModelName(String teacherModelName) { this.teacherModelName = teacherModelName; }

    public String getTeacherPromptVersion() { return teacherPromptVersion; }
    public void setTeacherPromptVersion(String teacherPromptVersion) { this.teacherPromptVersion = teacherPromptVersion; }

    public Double getTeacherTemperature() { return teacherTemperature; }
    public void setTeacherTemperature(Double teacherTemperature) { this.teacherTemperature = teacherTemperature; }

    public OffsetDateTime getLabeledAt() { return labeledAt; }
    public void setLabeledAt(OffsetDateTime labeledAt) { this.labeledAt = labeledAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
