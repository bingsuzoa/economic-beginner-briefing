package com.economicbriefing.reading.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "article_reading_history")
@Getter
@Setter
@NoArgsConstructor
public class ArticleReadingHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "article_id", nullable = false, length = 64)
    private String articleId;

    @Column(name = "read_at", nullable = false)
    private ZonedDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
        if (readAt == null) {
            readAt = ZonedDateTime.now();
        }
    }
}
