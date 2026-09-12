package com.economicbriefing.briefing;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyBriefingRepository extends JpaRepository<DailyBriefingEntity, String> {
    Optional<DailyBriefingEntity> findFirstByTargetDateAndStatusOrderByRevisionDesc(LocalDate date, String status);
    Optional<DailyBriefingEntity> findFirstByStatusOrderByFinishedAtDesc(String status);
    Optional<DailyBriefingEntity> findFirstByTargetDateOrderByRevisionDesc(LocalDate date);
    Page<DailyBriefingEntity> findAllByOrderByStartedAtDesc(Pageable pageable);
    Page<DailyBriefingEntity> findByStatusOrderByStartedAtDesc(String status, Pageable pageable);
    Optional<DailyBriefingEntity> findFirstByStatusOrderByStartedAtDesc(String status);
}
