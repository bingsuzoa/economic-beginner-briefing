package com.economicbriefing.economicflow.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import com.economicbriefing.economicflow.*;
import jakarta.persistence.*;

@Entity
@Table(name = "economic_events")
public class EconomicEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 40)
    private EventType eventType;
    @Column(nullable = false, columnDefinition = "TEXT") private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String subject;
    @Column(name = "subject_key", nullable = false, length = 128) private String subjectKey;
    @Enumerated(EnumType.STRING) @Column(name = "node_kind", length = 16) private NodeKind nodeKind;
    @Column(name = "scope_key", length = 128) private String scopeKey;
    @ManyToOne @JoinColumn(name = "slot_id") private EconomicSlotEntity slot;
    @ManyToOne @JoinColumn(name = "slot_value_id") private EconomicSlotValueEntity slotValue;
    @Column(name = "event_date", nullable = false) private LocalDate eventDate;
    @Column(name = "ended_at") private LocalDate endedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private EventStatus status;
    @Column(name = "previous_value", columnDefinition = "TEXT") private String previousValue;
    @Column(name = "previous_value_normalized", columnDefinition = "TEXT") private String previousValueNormalized;
    @Column(name = "new_value", columnDefinition = "TEXT") private String newValue;
    @Column(name = "new_value_normalized", columnDefinition = "TEXT") private String newValueNormalized;
    @Enumerated(EnumType.STRING) @Column(name = "value_unit", length = 16) private ValueUnit valueUnit;
    @Enumerated(EnumType.STRING) @Column(name = "value_type", length = 16) private ValueType valueType;
    @Column(name = "base_currency", length = 3) private String baseCurrency;
    @Column(name = "quote_currency", length = 3) private String quoteCurrency;
    @Column(name = "base_amount") private Integer baseAmount;
    @Enumerated(EnumType.STRING) @Column(name = "milestone_type", length = 32) private MilestoneType milestoneType;
    @Column(name = "milestone_period_value") private Integer milestonePeriodValue;
    @Enumerated(EnumType.STRING) @Column(name = "milestone_period_unit", length = 16)
    private MilestonePeriodUnit milestonePeriodUnit;
    @Column(name = "milestone_reference_date") private LocalDate milestoneReferenceDate;
    @Column(name = "region_code", length = 128) private String regionCode;
    @Column(name = "dedup_key", unique = true, length = 64) private String dedupKey;
    @ManyToMany
    @JoinTable(name = "event_topics", joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "topic_id"))
    private Set<TopicEntity> topics = new LinkedHashSet<>();
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @PrePersist void prePersist() { createdAt = updatedAt = OffsetDateTime.now(); }
    @PreUpdate void preUpdate() { updatedAt = OffsetDateTime.now(); }
    public Long getId() { return id; }
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getSubjectKey() { return subjectKey; }
    public void setSubjectKey(String subjectKey) { this.subjectKey = subjectKey; }
    public NodeKind getNodeKind() { return nodeKind; }
    public void setNodeKind(NodeKind value) { nodeKind = value; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String value) { scopeKey = value; }
    public EconomicSlotEntity getSlot() { return slot; }
    public void setSlot(EconomicSlotEntity value) { slot = value; }
    public EconomicSlotValueEntity getSlotValue() { return slotValue; }
    public void setSlotValue(EconomicSlotValueEntity value) { slotValue = value; }
    public LocalDate getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDate value) { endedAt = value; }
    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }
    public String getPreviousValue() { return previousValue; }
    public void setPreviousValue(String v) { previousValue = v; }
    public String getPreviousValueNormalized() { return previousValueNormalized; }
    public void setPreviousValueNormalized(String v) { previousValueNormalized = v; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String v) { newValue = v; }
    public String getNewValueNormalized() { return newValueNormalized; }
    public void setNewValueNormalized(String v) { newValueNormalized = v; }
    public ValueUnit getValueUnit() { return valueUnit; }
    public void setValueUnit(ValueUnit valueUnit) { this.valueUnit = valueUnit; }
    public ValueType getValueType() { return valueType; }
    public void setValueType(ValueType valueType) { this.valueType = valueType; }
    public String getBaseCurrency() { return baseCurrency; }
    public void setBaseCurrency(String baseCurrency) { this.baseCurrency = baseCurrency; }
    public String getQuoteCurrency() { return quoteCurrency; }
    public void setQuoteCurrency(String quoteCurrency) { this.quoteCurrency = quoteCurrency; }
    public Integer getBaseAmount() { return baseAmount; }
    public void setBaseAmount(Integer baseAmount) { this.baseAmount = baseAmount; }
    public MilestoneType getMilestoneType() { return milestoneType; }
    public void setMilestoneType(MilestoneType milestoneType) { this.milestoneType = milestoneType; }
    public Integer getMilestonePeriodValue() { return milestonePeriodValue; }
    public void setMilestonePeriodValue(Integer value) { milestonePeriodValue = value; }
    public MilestonePeriodUnit getMilestonePeriodUnit() { return milestonePeriodUnit; }
    public void setMilestonePeriodUnit(MilestonePeriodUnit unit) { milestonePeriodUnit = unit; }
    public LocalDate getMilestoneReferenceDate() { return milestoneReferenceDate; }
    public void setMilestoneReferenceDate(LocalDate date) { milestoneReferenceDate = date; }
    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
    public String getDedupKey() { return dedupKey; }
    public void setDedupKey(String dedupKey) { this.dedupKey = dedupKey; }
    public Set<TopicEntity> getTopics() { return topics; }
}
