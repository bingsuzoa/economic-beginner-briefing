package com.economicbriefing.economicflow.repository;

import java.util.List;
import com.economicbriefing.economicflow.entity.EconomicPrincipleChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EconomicPrincipleChunkRepository extends JpaRepository<EconomicPrincipleChunkEntity, Long> {
    List<EconomicPrincipleChunkEntity> findByActiveTrue();
}
