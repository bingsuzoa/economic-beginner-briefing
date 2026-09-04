package com.economicbriefing.economicflow.entity;

import java.time.OffsetDateTime;
import com.economicbriefing.economicflow.EventRelationType;
import com.economicbriefing.economicflow.RelationProvenance;
import jakarta.persistence.*;

@Entity
@Table(name = "event_relations", uniqueConstraints = @UniqueConstraint(columnNames = {"from_event_id", "to_event_id", "relation_type"}))
public class EventRelationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "from_event_id") private EconomicEventEntity fromEvent;
    @ManyToOne(optional = false) @JoinColumn(name = "to_event_id") private EconomicEventEntity toEvent;
    @Enumerated(EnumType.STRING) @Column(name = "relation_type", nullable = false, length = 32) private EventRelationType relationType;
    @Column(nullable = false) private double confidence;
    @Column(name = "evidence_article_id", length = 64) private String evidenceArticleId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private RelationProvenance provenance = RelationProvenance.STATE_TRANSITION;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @PrePersist void prePersist() { createdAt = OffsetDateTime.now(); }
    public void setFromEvent(EconomicEventEntity e) { fromEvent = e; }
    public void setToEvent(EconomicEventEntity e) { toEvent = e; }
    public void setRelationType(EventRelationType t) { relationType = t; }
    public void setConfidence(double value) { confidence = value; }
    public void setEvidenceArticleId(String id) { evidenceArticleId = id; }
    public Long getId() { return id; }
    public EconomicEventEntity getFromEvent() { return fromEvent; }
    public EconomicEventEntity getToEvent() { return toEvent; }
    public EventRelationType getRelationType() { return relationType; }
    public RelationProvenance getProvenance() { return provenance; }
    public String getEvidenceArticleId() { return evidenceArticleId; }
    public void setProvenance(RelationProvenance value) { provenance = value; }
}
