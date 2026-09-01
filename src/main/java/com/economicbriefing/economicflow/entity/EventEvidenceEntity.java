package com.economicbriefing.economicflow.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "event_evidence", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "article_id"}))
public class EventEvidenceEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "event_id") private EconomicEventEntity event;
    @Column(name = "article_id", nullable = false, length = 64) private String articleId;
    @Column(name = "evidence_text", nullable = false, columnDefinition = "TEXT") private String evidenceText;
    @Column(name = "source_type", nullable = false, length = 32) private String sourceType = "ARTICLE";
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @PrePersist void prePersist() { createdAt = OffsetDateTime.now(); }
    public void setEvent(EconomicEventEntity event) { this.event = event; }
    public void setArticleId(String articleId) { this.articleId = articleId; }
    public void setEvidenceText(String evidenceText) { this.evidenceText = evidenceText; }
}
