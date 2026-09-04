package com.economicbriefing.classifier.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "relation_explanation_assets")
public class RelationExplanationAssetEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "relation_key", nullable = false, length = 512) private String relationKey;
    @Column(name = "relation_from", nullable = false, columnDefinition = "TEXT") private String from;
    @Column(name = "relation_to", nullable = false, columnDefinition = "TEXT") private String to;
    @Column(name = "relation_type", nullable = false, length = 64) private String relationType;
    @Column(nullable = false, columnDefinition = "TEXT") private String explanation;
    @Column(name = "explanation_kind", nullable = false, length = 32) private String explanationKind;
    @Column(name = "source_article_id", nullable = false, length = 64) private String sourceArticleId;
    @Column(name = "source_reference", nullable = false, length = 256) private String sourceReference;
    @Column(name = "source_evidence", columnDefinition = "TEXT") private String sourceEvidence;
    @Column(name = "principle_chunk_ids", columnDefinition = "TEXT") private String principleChunkIds;
    @Column(name = "model_name", length = 128) private String modelName;
    @Column(name = "presenter_prompt_version", nullable = false, length = 64) private String promptVersion;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @PrePersist void prePersist() { if (createdAt == null) createdAt = OffsetDateTime.now(); }
    public String getRelationKey() { return relationKey; } public void setRelationKey(String v) { relationKey = v; }
    public String getFrom() { return from; } public void setFrom(String v) { from = v; }
    public String getTo() { return to; } public void setTo(String v) { to = v; }
    public String getRelationType() { return relationType; } public void setRelationType(String v) { relationType = v; }
    public String getExplanation() { return explanation; } public void setExplanation(String v) { explanation = v; }
    public String getExplanationKind() { return explanationKind; } public void setExplanationKind(String v) { explanationKind = v; }
    public String getSourceArticleId() { return sourceArticleId; } public void setSourceArticleId(String v) { sourceArticleId = v; }
    public String getSourceReference() { return sourceReference; } public void setSourceReference(String v) { sourceReference = v; }
    public String getSourceEvidence() { return sourceEvidence; } public void setSourceEvidence(String v) { sourceEvidence = v; }
    public String getPrincipleChunkIds() { return principleChunkIds; } public void setPrincipleChunkIds(String v) { principleChunkIds = v; }
    public String getModelName() { return modelName; } public void setModelName(String v) { modelName = v; }
    public String getPromptVersion() { return promptVersion; } public void setPromptVersion(String v) { promptVersion = v; }
}
