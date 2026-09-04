package com.economicbriefing.classifier.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "article_presentations")
public class ArticlePresentationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "article_id", nullable = false, length = 64) private String articleId;
    @Column(name = "briefing_id", nullable = false, length = 128) private String briefingId;
    @Column(name = "presentation_json", nullable = false, columnDefinition = "TEXT") private String presentationJson;
    @Column(name = "model_name", length = 128) private String modelName;
    @Column(name = "presenter_prompt_version", nullable = false, length = 64) private String promptVersion;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @PrePersist void prePersist() { if (createdAt == null) createdAt = OffsetDateTime.now(); }
    public String getArticleId() { return articleId; } public void setArticleId(String v) { articleId = v; }
    public String getBriefingId() { return briefingId; } public void setBriefingId(String v) { briefingId = v; }
    public String getPresentationJson() { return presentationJson; } public void setPresentationJson(String v) { presentationJson = v; }
    public String getModelName() { return modelName; } public void setModelName(String v) { modelName = v; }
    public String getPromptVersion() { return promptVersion; } public void setPromptVersion(String v) { promptVersion = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
