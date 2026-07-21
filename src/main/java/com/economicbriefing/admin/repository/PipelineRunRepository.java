package com.economicbriefing.admin.repository;

import java.util.Optional;

import com.economicbriefing.admin.entity.PipelineRunEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PipelineRunRepository extends JpaRepository<PipelineRunEntity, String> {

    @Query("SELECT r FROM PipelineRunEntity r WHERE r.status = 'RUNNING' ORDER BY r.startedAt DESC")
    Optional<PipelineRunEntity> findRunning();

    Page<PipelineRunEntity> findAllByOrderByStartedAtDesc(Pageable pageable);

    Page<PipelineRunEntity> findByStatusOrderByStartedAtDesc(String status, Pageable pageable);

    Page<PipelineRunEntity> findByTriggerTypeOrderByStartedAtDesc(String triggerType, Pageable pageable);

    Page<PipelineRunEntity> findByStatusAndTriggerTypeOrderByStartedAtDesc(
            String status, String triggerType, Pageable pageable);
}
