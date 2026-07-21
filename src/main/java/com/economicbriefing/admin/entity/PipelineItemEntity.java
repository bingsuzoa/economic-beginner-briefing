package com.economicbriefing.admin.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "pipeline_items")
public class PipelineItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "article_url", columnDefinition = "TEXT")
    private String articleUrl;

    @Column(name = "normalized_url", columnDefinition = "TEXT")
    private String normalizedUrl;

    @Column(length = 128)
    private String source;

    @Column(name = "original_title", columnDefinition = "TEXT")
    private String originalTitle;

    @Column(name = "original_summary", columnDefinition = "TEXT")
    private String originalSummary;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(length = 64)
    private String category;

    @Column(name = "duplicate_status", nullable = false, length = 20)
    private String duplicateStatus = "UNIQUE";

    @Column(name = "analysis_status", nullable = false, length = 20)
    private String analysisStatus = "PENDING";

    @Column(name = "analysis_result_json", columnDefinition = "TEXT")
    private String analysisResultJson;

    @Column(name = "analysis_error_message", columnDefinition = "TEXT")
    private String analysisErrorMessage;

    @Column(name = "publish_status", nullable = false, length = 20)
    private String publishStatus = "PENDING";

    @Column(name = "notion_page_id", length = 128)
    private String notionPageId;

    @Column(name = "notion_page_url", columnDefinition = "TEXT")
    private String notionPageUrl;

    @Column(name = "publish_error_message", columnDefinition = "TEXT")
    private String publishErrorMessage;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getArticleUrl() { return articleUrl; }
    public void setArticleUrl(String articleUrl) { this.articleUrl = articleUrl; }
    public String getNormalizedUrl() { return normalizedUrl; }
    public void setNormalizedUrl(String normalizedUrl) { this.normalizedUrl = normalizedUrl; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getOriginalTitle() { return originalTitle; }
    public void setOriginalTitle(String originalTitle) { this.originalTitle = originalTitle; }
    public String getOriginalSummary() { return originalSummary; }
    public void setOriginalSummary(String originalSummary) { this.originalSummary = originalSummary; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDuplicateStatus() { return duplicateStatus; }
    public void setDuplicateStatus(String duplicateStatus) { this.duplicateStatus = duplicateStatus; }
    public String getAnalysisStatus() { return analysisStatus; }
    public void setAnalysisStatus(String analysisStatus) { this.analysisStatus = analysisStatus; }
    public String getAnalysisResultJson() { return analysisResultJson; }
    public void setAnalysisResultJson(String analysisResultJson) { this.analysisResultJson = analysisResultJson; }
    public String getAnalysisErrorMessage() { return analysisErrorMessage; }
    public void setAnalysisErrorMessage(String analysisErrorMessage) { this.analysisErrorMessage = analysisErrorMessage; }
    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }
    public String getNotionPageId() { return notionPageId; }
    public void setNotionPageId(String notionPageId) { this.notionPageId = notionPageId; }
    public String getNotionPageUrl() { return notionPageUrl; }
    public void setNotionPageUrl(String notionPageUrl) { this.notionPageUrl = notionPageUrl; }
    public String getPublishErrorMessage() { return publishErrorMessage; }
    public void setPublishErrorMessage(String publishErrorMessage) { this.publishErrorMessage = publishErrorMessage; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
