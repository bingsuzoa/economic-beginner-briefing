package com.economicbriefing.classifier.repository;

import java.util.List;

import com.economicbriefing.classifier.entity.ArticleAnalyzerResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleAnalyzerResultRepository extends JpaRepository<ArticleAnalyzerResultEntity, Long> {

    List<ArticleAnalyzerResultEntity> findByArticleIdOrderByCreatedAtDesc(String articleId);
}
