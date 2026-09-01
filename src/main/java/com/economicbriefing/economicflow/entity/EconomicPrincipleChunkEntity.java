package com.economicbriefing.economicflow.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "economic_principle_chunks")
public class EconomicPrincipleChunkEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(nullable = false, columnDefinition = "TEXT") private String concepts = "";
    @Column(name = "from_concept") private String fromConcept;
    @Column(name = "to_concept") private String toConcept;
    private String mechanism;
    @Column(name = "source_type", nullable = false) private String sourceType;
    @Column(name = "source_title", nullable = false) private String sourceTitle;
    @Column(name = "source_section") private String sourceSection;
    @Column(nullable = false) private boolean active = true;

    public Long getId() { return id; }
    public String getContent() { return content; }
    public void setContent(String value) { content = value; }
    public String getConcepts() { return concepts; }
    public void setConcepts(String value) { concepts = value; }
    public String getFromConcept() { return fromConcept; }
    public void setFromConcept(String value) { fromConcept = value; }
    public String getToConcept() { return toConcept; }
    public void setToConcept(String value) { toConcept = value; }
    public String getMechanism() { return mechanism; }
    public void setMechanism(String value) { mechanism = value; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String value) { sourceType = value; }
    public String getSourceTitle() { return sourceTitle; }
    public void setSourceTitle(String value) { sourceTitle = value; }
    public String getSourceSection() { return sourceSection; }
    public void setSourceSection(String value) { sourceSection = value; }
    public boolean isActive() { return active; }
}
