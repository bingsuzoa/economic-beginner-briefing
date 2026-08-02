package com.economicbriefing.reading.repository;

import com.economicbriefing.reading.entity.ArticleReadingHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleReadingHistoryRepository extends JpaRepository<ArticleReadingHistoryEntity, Long> {

    Optional<ArticleReadingHistoryEntity> findByUserIdAndArticleId(String userId, String articleId);

    @Query("SELECT h.articleId FROM ArticleReadingHistoryEntity h WHERE h.userId = :userId AND h.articleId IN :articleIds")
    List<String> findReadArticleIds(@Param("userId") String userId, @Param("articleIds") List<String> articleIds);

    @Query("SELECT h FROM ArticleReadingHistoryEntity h WHERE h.userId = :userId AND h.articleId IN :articleIds")
    List<ArticleReadingHistoryEntity> findByUserIdAndArticleIdIn(@Param("userId") String userId, @Param("articleIds") List<String> articleIds);
}
