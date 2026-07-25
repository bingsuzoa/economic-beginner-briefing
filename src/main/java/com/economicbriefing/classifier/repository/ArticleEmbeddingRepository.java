package com.economicbriefing.classifier.repository;

import java.util.Optional;

import com.economicbriefing.classifier.entity.ArticleEmbeddingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleEmbeddingRepository extends JpaRepository<ArticleEmbeddingEntity, Long> {

    Optional<ArticleEmbeddingEntity> findByArticleIdAndEmbeddingModel(
            String articleId, String embeddingModel);
}
