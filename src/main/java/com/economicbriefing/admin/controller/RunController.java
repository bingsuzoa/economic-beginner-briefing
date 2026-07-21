package com.economicbriefing.admin.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.economicbriefing.admin.dto.ApiResponse;
import com.economicbriefing.admin.dto.PageResponse;
import com.economicbriefing.admin.entity.PipelineLogEntity;
import com.economicbriefing.admin.entity.PipelineRunEntity;
import com.economicbriefing.admin.repository.PipelineItemRepository;
import com.economicbriefing.admin.repository.PipelineLogRepository;
import com.economicbriefing.admin.repository.PipelineRunRepository;
import com.economicbriefing.config.AdminProperties;
import com.economicbriefing.pipeline.BriefingPipeline;
import com.economicbriefing.pipeline.PipelineLock;
import com.economicbriefing.pipeline.PipelineOptions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class RunController {

    private final PipelineRunRepository runRepo;
    private final PipelineLogRepository logRepo;
    private final PipelineItemRepository itemRepo;
    private final PipelineLock pipelineLock;
    private final BriefingPipeline pipeline;
    private final AdminProperties adminProperties;

    public RunController(
            PipelineRunRepository runRepo,
            PipelineLogRepository logRepo,
            PipelineItemRepository itemRepo,
            PipelineLock pipelineLock,
            BriefingPipeline pipeline,
            AdminProperties adminProperties) {
        this.runRepo = runRepo;
        this.logRepo = logRepo;
        this.itemRepo = itemRepo;
        this.pipelineLock = pipelineLock;
        this.pipeline = pipeline;
        this.adminProperties = adminProperties;
    }

    @GetMapping("/runs")
    public ApiResponse<PageResponse<PipelineRunEntity>> listRuns(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "0") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggerType) {

        int pageSize = size > 0 ? size : adminProperties.defaultPageSize();
        PageRequest pageRequest = PageRequest.of(Math.max(0, page - 1), pageSize);

        Page<PipelineRunEntity> result;
        if (status != null && triggerType != null) {
            result = runRepo.findByStatusAndTriggerTypeOrderByStartedAtDesc(status, triggerType, pageRequest);
        } else if (status != null) {
            result = runRepo.findByStatusOrderByStartedAtDesc(status, pageRequest);
        } else if (triggerType != null) {
            result = runRepo.findByTriggerTypeOrderByStartedAtDesc(triggerType, pageRequest);
        } else {
            result = runRepo.findAllByOrderByStartedAtDesc(pageRequest);
        }

        PageResponse<PipelineRunEntity> pageResponse = new PageResponse<>(
                result.getContent(), result.getTotalElements(), page, pageSize);

        return ApiResponse.ok(pageResponse);
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<ApiResponse<?>> getRunDetail(@PathVariable String runId) {
        return runRepo.findById(runId)
                .<ResponseEntity<ApiResponse<?>>>map(run -> ResponseEntity.ok(ApiResponse.ok(run)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("RUN_NOT_FOUND", "실행 기록을 찾을 수 없습니다.")));
    }

    @GetMapping("/runs/{runId}/logs")
    public ResponseEntity<ApiResponse<?>> getRunLogs(
            @PathVariable String runId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String step) {

        if (runRepo.findById(runId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("RUN_NOT_FOUND", "실행 기록을 찾을 수 없습니다."));
        }

        List<PipelineLogEntity> logs;
        if (level != null && step != null) {
            logs = logRepo.findByRunIdAndLevelAndStepOrderByCreatedAtAsc(runId, level, step);
        } else if (level != null) {
            logs = logRepo.findByRunIdAndLevelOrderByCreatedAtAsc(runId, level);
        } else if (step != null) {
            logs = logRepo.findByRunIdAndStepOrderByCreatedAtAsc(runId, step);
        } else {
            logs = logRepo.findByRunIdOrderByCreatedAtAsc(runId);
        }

        return ResponseEntity.ok(ApiResponse.ok(logs));
    }

    @GetMapping("/runs/{runId}/items")
    public ResponseEntity<ApiResponse<?>> getRunItems(
            @PathVariable String runId,
            @RequestParam(required = false) String analysisStatus,
            @RequestParam(required = false) String publishStatus) {

        if (runRepo.findById(runId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("RUN_NOT_FOUND", "실행 기록을 찾을 수 없습니다."));
        }

        var items = analysisStatus != null
                ? itemRepo.findByRunIdAndAnalysisStatusOrderByIdAsc(runId, analysisStatus)
                : publishStatus != null
                ? itemRepo.findByRunIdAndPublishStatusOrderByIdAsc(runId, publishStatus)
                : itemRepo.findByRunIdOrderByIdAsc(runId);

        return ResponseEntity.ok(ApiResponse.ok(items));
    }

    @PostMapping("/runs")
    public ResponseEntity<ApiResponse<?>> triggerRun(@RequestBody(required = false) Map<String, String> body) {
        String targetDateStr = body != null ? body.get("targetDate") : null;

        if (targetDateStr != null) {
            try {
                LocalDate.parse(targetDateStr);
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("INVALID_TARGET_DATE",
                                "유효하지 않은 날짜 형식입니다: " + targetDateStr));
            }
        }

        if (pipelineLock.isLocked()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("PIPELINE_ALREADY_RUNNING", "파이프라인이 이미 실행 중입니다."));
        }

        PipelineOptions options = targetDateStr != null
                ? PipelineOptions.manual(LocalDate.parse(targetDateStr))
                : PipelineOptions.hourly();

        // Run async
        Thread.startVirtualThread(() -> pipeline.run(options));

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(Map.of("message", "파이프라인 실행이 시작되었습니다.")));
    }
}
