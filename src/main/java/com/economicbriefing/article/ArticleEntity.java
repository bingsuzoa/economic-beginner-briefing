package com.economicbriefing.article;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "articles")
public class ArticleEntity {
    @Id private String id;
    @Column(nullable = false, length = 128) private String source;
    @Column(name = "source_article_id", length = 256) private String sourceArticleId;
    @Column(nullable = false, columnDefinition = "TEXT") private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String summary = "";
    @Column(columnDefinition = "TEXT") private String body;
    @Column(name = "body_status", nullable = false, length = 16) private String bodyStatus = "RSS_ONLY";
    @Column(nullable = false, columnDefinition = "TEXT") private String url;
    @Column(name = "published_at") private OffsetDateTime publishedAt;
    @Column(name = "collected_at", nullable = false) private OffsetDateTime collectedAt;
    @Column(name = "body_fetched_at") private OffsetDateTime bodyFetchedAt;
    @Column(name = "content_hash", length = 128) private String contentHash;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    @PrePersist void create() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
    @PreUpdate void update() { updatedAt = OffsetDateTime.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSourceArticleId() { return sourceArticleId; }
    public void setSourceArticleId(String sourceArticleId) { this.sourceArticleId = sourceArticleId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary == null ? "" : summary; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getBodyStatus() { return bodyStatus; }
    public void setBodyStatus(String bodyStatus) { this.bodyStatus = bodyStatus; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public OffsetDateTime getCollectedAt() { return collectedAt; }
    public void setCollectedAt(OffsetDateTime collectedAt) { this.collectedAt = collectedAt; }
    public OffsetDateTime getBodyFetchedAt() { return bodyFetchedAt; }
    public void setBodyFetchedAt(OffsetDateTime bodyFetchedAt) { this.bodyFetchedAt = bodyFetchedAt; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
