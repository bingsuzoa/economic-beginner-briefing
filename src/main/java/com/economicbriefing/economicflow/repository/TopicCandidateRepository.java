package com.economicbriefing.economicflow.repository;

import com.economicbriefing.economicflow.entity.TopicCandidateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicCandidateRepository extends JpaRepository<TopicCandidateEntity, Long> {
    boolean existsByNameAndArticleId(String name, String articleId);
}
