package com.economicbriefing.classifier.repository;

import com.economicbriefing.classifier.entity.ArticlePresentationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticlePresentationRepository extends JpaRepository<ArticlePresentationEntity, Long> {
    List<ArticlePresentationEntity> findByArticleIdOrderByCreatedAtDesc(String articleId);
}
