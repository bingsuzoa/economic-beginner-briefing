package com.economicbriefing.economicflow.repository;

import java.util.Optional;
import com.economicbriefing.economicflow.entity.EconomicSlotValueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EconomicSlotValueRepository extends JpaRepository<EconomicSlotValueEntity, Long> {
    Optional<EconomicSlotValueEntity> findBySlot_IdAndValueKeyAndActiveTrue(Long slotId, String valueKey);
}
