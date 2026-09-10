package com.economicbriefing.article;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleRepository extends JpaRepository<ArticleEntity, String> {
    Optional<ArticleEntity> findByUrl(String url);
    Optional<ArticleEntity> findBySourceAndSourceArticleId(String source, String sourceArticleId);
    List<ArticleEntity> findByPublishedAtGreaterThanEqualAndPublishedAtLessThanOrderByPublishedAtAsc(
            OffsetDateTime start, OffsetDateTime end);
}
