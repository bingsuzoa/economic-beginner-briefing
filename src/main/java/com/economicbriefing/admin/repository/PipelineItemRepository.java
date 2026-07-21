package com.economicbriefing.admin.repository;

import java.util.List;

import com.economicbriefing.admin.entity.PipelineItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineItemRepository extends JpaRepository<PipelineItemEntity, Long> {

    List<PipelineItemEntity> findByRunIdOrderByIdAsc(String runId);

    List<PipelineItemEntity> findByRunIdAndAnalysisStatusOrderByIdAsc(String runId, String analysisStatus);

    List<PipelineItemEntity> findByRunIdAndPublishStatusOrderByIdAsc(String runId, String publishStatus);
}
