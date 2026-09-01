package com.economicbriefing.economicflow.entity;

import java.time.OffsetDateTime;

import com.economicbriefing.economicflow.TopicDomain;
import jakarta.persistence.*;

@Entity
@Table(name = "topics")
public class TopicEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "topic_key", nullable = false, unique = true, length = 64)
    private String topicKey;
    @Column(nullable = false, length = 128)
    private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private TopicDomain domain;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "aliases", columnDefinition = "TEXT")
    private String aliases;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @PrePersist void prePersist() { if (createdAt == null) createdAt = OffsetDateTime.now(); }
    public Long getId() { return id; }
    public String getTopicKey() { return topicKey; }
    public void setTopicKey(String topicKey) { this.topicKey = topicKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public TopicDomain getDomain() { return domain; }
    public void setDomain(TopicDomain domain) { this.domain = domain; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getAliases() { return aliases; }
    public void setAliases(String aliases) { this.aliases = aliases; }
}
