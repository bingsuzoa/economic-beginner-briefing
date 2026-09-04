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
@Table(name = "article_analyzer_results")
public class ArticleAnalyzerResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false, length = 64)
    private String articleId;

    @Column(name = "briefing_id", length = 128)
    private String briefingId;

    @Column(name = "analysis_json", nullable = false, columnDefinition = "TEXT")
    private String analysisJson;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "analyzer_prompt_version", length = 64)
    private String promptVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getArticleId() { return articleId; }
    public void setArticleId(String articleId) { this.articleId = articleId; }
    public String getBriefingId() { return briefingId; }
    public void setBriefingId(String briefingId) { this.briefingId = briefingId; }
    public String getAnalysisJson() { return analysisJson; }
    public void setAnalysisJson(String analysisJson) { this.analysisJson = analysisJson; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public String getAnalyzerPromptVersion() { return promptVersion; }
    public void setAnalyzerPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
