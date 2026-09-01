package com.economicbriefing.economicflow.repository;

import com.economicbriefing.economicflow.entity.EventEvidenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventEvidenceRepository extends JpaRepository<EventEvidenceEntity, Long> {
    boolean existsByEvent_IdAndArticleId(Long eventId, String articleId);
    long countByEvent_Id(Long eventId);
}
