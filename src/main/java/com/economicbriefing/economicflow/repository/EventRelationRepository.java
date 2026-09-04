package com.economicbriefing.economicflow.repository;

import com.economicbriefing.economicflow.EventRelationType;
import com.economicbriefing.economicflow.entity.EventRelationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRelationRepository extends JpaRepository<EventRelationEntity, Long> {
    boolean existsByFromEvent_IdAndToEvent_IdAndRelationType(Long fromId, Long toId, EventRelationType type);
    java.util.Optional<EventRelationEntity> findByFromEvent_IdAndToEvent_IdAndRelationType(
            Long fromId, Long toId, EventRelationType type);
    java.util.List<EventRelationEntity> findByToEvent_IdInAndProvenance(
            java.util.Collection<Long> toIds, com.economicbriefing.economicflow.RelationProvenance provenance);
    java.util.List<EventRelationEntity> findByFromEvent_IdIn(java.util.Collection<Long> fromIds);
    java.util.List<EventRelationEntity> findByToEvent_IdIn(java.util.Collection<Long> toIds);
}
