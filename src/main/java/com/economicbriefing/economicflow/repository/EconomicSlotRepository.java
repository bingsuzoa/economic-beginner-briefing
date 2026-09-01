package com.economicbriefing.economicflow.repository;

import java.util.Optional;
import com.economicbriefing.economicflow.entity.EconomicSlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EconomicSlotRepository extends JpaRepository<EconomicSlotEntity, Long> {
    Optional<EconomicSlotEntity> findBySlotKeyAndActiveTrue(String slotKey);
}
