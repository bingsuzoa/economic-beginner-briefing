package com.economicbriefing.classifier.repository;

import com.economicbriefing.classifier.entity.RelationExplanationAssetEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RelationExplanationAssetRepository extends JpaRepository<RelationExplanationAssetEntity, Long> {
    Optional<RelationExplanationAssetEntity> findFirstByRelationKeyAndExplanationKindOrderByIdDesc(String relationKey, String explanationKind);
    boolean existsByRelationKeyAndExplanationKindAndSourceArticleId(String relationKey, String explanationKind, String sourceArticleId);
}
