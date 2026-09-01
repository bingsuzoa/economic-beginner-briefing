package com.economicbriefing.economicflow.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "topic_candidates", uniqueConstraints = @UniqueConstraint(columnNames = {"name", "article_id"}))
public class TopicCandidateEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 128) private String name;
    @Column(name = "article_id", nullable = false, length = 64) private String articleId;
    @Column(nullable = false, length = 20) private String status = "PENDING";
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @PrePersist void prePersist() { createdAt = OffsetDateTime.now(); }
    public void setName(String name) { this.name = name; }
    public void setArticleId(String articleId) { this.articleId = articleId; }
}
