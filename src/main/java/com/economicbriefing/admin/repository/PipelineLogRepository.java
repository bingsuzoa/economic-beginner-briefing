package com.economicbriefing.admin.repository;

import java.util.List;

import com.economicbriefing.admin.entity.PipelineLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineLogRepository extends JpaRepository<PipelineLogEntity, Long> {

    List<PipelineLogEntity> findByRunIdOrderByCreatedAtAsc(String runId);

    List<PipelineLogEntity> findByRunIdAndLevelOrderByCreatedAtAsc(String runId, String level);

    List<PipelineLogEntity> findByRunIdAndStepOrderByCreatedAtAsc(String runId, String step);

    List<PipelineLogEntity> findByRunIdAndLevelAndStepOrderByCreatedAtAsc(String runId, String level, String step);
}
