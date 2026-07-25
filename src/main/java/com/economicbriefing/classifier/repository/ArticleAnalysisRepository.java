package com.economicbriefing.classifier.repository;

import java.util.List;

import com.economicbriefing.classifier.entity.ArticleAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleAnalysisRepository extends JpaRepository<ArticleAnalysisEntity, Long> {

    List<ArticleAnalysisEntity> findByArticleId(String articleId);
}
