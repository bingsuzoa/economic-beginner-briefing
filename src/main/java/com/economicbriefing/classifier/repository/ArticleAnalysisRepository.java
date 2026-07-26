package com.economicbriefing.classifier.repository;

import java.util.List;

import com.economicbriefing.classifier.entity.ArticleAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleAnalysisRepository extends JpaRepository<ArticleAnalysisEntity, Long> {

    List<ArticleAnalysisEntity> findByArticleId(String articleId);

    /**
     * Newest first. Plain {@code findAll()} returns rows in physical order, which is roughly
     * insertion order, so every fresh analysis landed at the bottom of the public page.
     */
    List<ArticleAnalysisEntity> findAllByOrderByCreatedAtDesc();
}
