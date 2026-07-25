package com.economicbriefing.api;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.economicbriefing.admin.entity.PipelineRunEntity;
import com.economicbriefing.admin.repository.PipelineRunRepository;
import com.economicbriefing.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated monitoring endpoint answering one question: are briefings still being
 * produced?
 *
 * <p>Watching the scheduler's configured state alone only catches a bad cron. The symptom that
 * actually matters — no fresh briefings — also comes from a pipeline failing every hour, an
 * expired OpenAI key, or every RSS source going down. Staleness of the last successful run
 * covers all of those, so it is the primary signal here.
 */
@RestController
@RequestMapping("/api/health")
public class BriefingHealthController {

    private static final Logger log = LoggerFactory.getLogger(BriefingHealthController.class);

    private final PipelineRunRepository runRepo;
    private final AppProperties appProperties;
    private final Duration maxSuccessAge;

    public BriefingHealthController(
            PipelineRunRepository runRepo,
            AppProperties appProperties,
            @Value("${briefing.health.max-success-age:3h}") Duration maxSuccessAge) {
        this.runRepo = runRepo;
        this.appProperties = appProperties;
        this.maxSuccessAge = maxSuccessAge;
    }

    @GetMapping("/briefing")
    public ResponseEntity<BriefingHealthResponse> briefingHealth() {
        List<String> reasons = new ArrayList<>();
        AppProperties.SchedulerProperties scheduler = appProperties.scheduler();

        boolean dbConnected = true;
        OffsetDateTime lastSuccessAt = null;
        try {
            lastSuccessAt = runRepo.findFirstByStatusOrderByStartedAtDesc("SUCCESS")
                    .map(BriefingHealthController::completionTime)
                    .orElse(null);
        } catch (Exception e) {
            dbConnected = false;
            reasons.add("database unreachable: " + e.getClass().getSimpleName());
            log.error("[Health] database check failed: {}", e.getMessage());
        }

        if ("MISCONFIGURED".equals(scheduler.state())) {
            reasons.add("scheduler cron is invalid: '" + scheduler.cron() + "'");
        }

        Long ageMinutes = lastSuccessAt != null
                ? Duration.between(lastSuccessAt, OffsetDateTime.now()).toMinutes()
                : null;

        // Only meaningful when something is supposed to be running. With the scheduler off
        // (the local default) stale data is expected, not a fault.
        if (dbConnected && scheduler.enabled()) {
            if (lastSuccessAt == null) {
                reasons.add("no successful pipeline run recorded yet");
            } else if (ageMinutes > maxSuccessAge.toMinutes()) {
                reasons.add("no successful run in " + ageMinutes + "m (limit "
                        + maxSuccessAge.toMinutes() + "m)");
            }
        }

        boolean up = reasons.isEmpty();
        BriefingHealthResponse body = new BriefingHealthResponse(
                up ? "UP" : "DOWN",
                scheduler.state(),
                scheduler.cron(),
                dbConnected,
                lastSuccessAt != null ? lastSuccessAt.toString() : null,
                ageMinutes,
                reasons);

        if (!up) {
            log.warn("[Health] DOWN: {}", reasons);
        }

        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /** A run that never recorded a finish time still started, so fall back to that. */
    private static OffsetDateTime completionTime(PipelineRunEntity run) {
        return run.getFinishedAt() != null ? run.getFinishedAt() : run.getStartedAt();
    }
}
