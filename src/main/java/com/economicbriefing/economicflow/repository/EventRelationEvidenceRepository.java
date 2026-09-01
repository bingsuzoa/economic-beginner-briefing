package com.economicbriefing.economicflow.repository;

import com.economicbriefing.economicflow.entity.EventRelationEvidenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRelationEvidenceRepository extends JpaRepository<EventRelationEvidenceEntity, Long> {
    boolean existsByRelation_IdAndArticleIdAndEvidenceHash(Long relationId, String articleId, String evidenceHash);
}
