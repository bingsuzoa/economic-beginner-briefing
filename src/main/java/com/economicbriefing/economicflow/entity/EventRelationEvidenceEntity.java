package com.economicbriefing.economicflow.entity;

import java.time.OffsetDateTime;
import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import jakarta.persistence.*;

@Entity
@Table(name = "event_relation_evidence", uniqueConstraints = @UniqueConstraint(
        columnNames = {"relation_id", "article_id", "evidence_hash"}))
public class EventRelationEvidenceEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "relation_id") private EventRelationEntity relation;
    @Column(name = "article_id", nullable = false, length = 64) private String articleId;
    @Column(name = "evidence_text", nullable = false, columnDefinition = "TEXT") private String evidenceText;
    @Column(name = "evidence_hash", nullable = false, length = 64) private String evidenceHash;
    @Enumerated(EnumType.STRING) @Column(name = "evidence_type", nullable = false, length = 32)
    private ArticleAnalysisResponse.StatementType evidenceType;
    @Column(length = 256) private String speaker;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @PrePersist void prePersist() { createdAt = OffsetDateTime.now(); }
    public void setRelation(EventRelationEntity value) { relation = value; }
    public void setArticleId(String value) { articleId = value; }
    public void setEvidenceText(String value) { evidenceText = value; }
    public void setEvidenceHash(String value) { evidenceHash = value; }
    public void setEvidenceType(ArticleAnalysisResponse.StatementType value) { evidenceType = value; }
    public void setSpeaker(String value) { speaker = value; }
    public EventRelationEntity getRelation() { return relation; }
}
