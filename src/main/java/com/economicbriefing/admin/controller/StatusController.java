package com.economicbriefing.admin.controller;

import com.economicbriefing.admin.dto.ApiResponse;
import com.economicbriefing.admin.dto.StatusResponse;
import com.economicbriefing.admin.repository.PipelineRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class StatusController {

    private static final Logger log = LoggerFactory.getLogger(StatusController.class);

    private final PipelineRunRepository runRepo;

    public StatusController(PipelineRunRepository runRepo) {
        this.runRepo = runRepo;
    }

    @GetMapping("/status")
    public ApiResponse<StatusResponse> getStatus() {
        boolean dbConnected;
        try {
            runRepo.count();
            dbConnected = true;
        } catch (Exception e) {
            log.error("DB connection check failed: {}", e.getMessage());
            dbConnected = false;
        }

        var running = dbConnected ? runRepo.findRunning().orElse(null) : null;
        var lastRunPage = dbConnected
                ? runRepo.findAllByOrderByStartedAtDesc(PageRequest.of(0, 1))
                : null;
        var lastRun = lastRunPage != null && lastRunPage.hasContent()
                ? lastRunPage.getContent().get(0) : null;

        StatusResponse.LastRunInfo lastRunInfo = null;
        if (lastRun != null) {
            lastRunInfo = new StatusResponse.LastRunInfo(
                    lastRun.getId(),
                    lastRun.getStatus(),
                    lastRun.getStartedAt() != null ? lastRun.getStartedAt().toString() : null,
                    lastRun.getFinishedAt() != null ? lastRun.getFinishedAt().toString() : null,
                    lastRun.getTriggerType()
            );
        }

        StatusResponse status = new StatusResponse(
                "running",
                running != null,
                running != null ? running.getId() : null,
                running != null ? running.getCurrentStep() : null,
                lastRunInfo,
                dbConnected
        );

        return ApiResponse.ok(status);
    }
}
