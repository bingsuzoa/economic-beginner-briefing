package com.economicbriefing.classifier.repository;

import java.util.List;

import com.economicbriefing.classifier.entity.ArticleRouterResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleRouterResultRepository extends JpaRepository<ArticleRouterResultEntity, Long> {

    List<ArticleRouterResultEntity> findByArticleIdOrderByCreatedAtDesc(String articleId);
}
