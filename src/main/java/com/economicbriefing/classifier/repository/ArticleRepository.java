package com.economicbriefing.classifier.repository;

import java.util.Optional;

import com.economicbriefing.classifier.entity.ArticleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleRepository extends JpaRepository<ArticleEntity, String> {

    Optional<ArticleEntity> findByContentHash(String contentHash);

    Optional<ArticleEntity> findBySourceAndSourceArticleId(String source, String sourceArticleId);
}
