package com.economicbriefing.economicflow.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.economicbriefing.economicflow.EventType;
import com.economicbriefing.economicflow.entity.EconomicEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EconomicEventRepository extends JpaRepository<EconomicEventEntity, Long> {
    Optional<EconomicEventEntity> findByDedupKey(String dedupKey);
    List<EconomicEventEntity> findBySubjectKey(String subjectKey);
    List<EconomicEventEntity> findTop20ByTitleContainingIgnoreCaseOrderByIdDesc(String text);
    List<EconomicEventEntity> findTop50ByOrderByIdDesc();
    List<EconomicEventEntity> findByEventTypeAndEventDateBetween(EventType type, LocalDate from, LocalDate to);
    List<EconomicEventEntity> findBySubjectKeyAndEventDateBeforeOrderByEventDateDesc(String subjectKey, LocalDate date);

    @Query("""
            select e from EconomicEventEntity e
            where e.nodeKind = com.economicbriefing.economicflow.NodeKind.STATE
              and e.scopeKey = :scopeKey and e.subjectKey = :subjectKey
              and e.slot.id = :slotId and e.endedAt is null
            order by e.eventDate desc, e.id desc
            """)
    List<EconomicEventEntity> findActiveState(@Param("scopeKey") String scopeKey,
            @Param("subjectKey") String subjectKey, @Param("slotId") Long slotId);

    @Query("""
            select e from EconomicEventEntity e
            where e.scopeKey = :scopeKey and e.subjectKey = :subjectKey and e.slot.id = :slotId
            order by e.eventDate desc, e.id desc
            """)
    List<EconomicEventEntity> findRecentNormalizedNodes(@Param("scopeKey") String scopeKey,
            @Param("subjectKey") String subjectKey, @Param("slotId") Long slotId,
            org.springframework.data.domain.Pageable pageable);
}
