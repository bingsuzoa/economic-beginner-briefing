package com.economicbriefing.scheduler;

import com.economicbriefing.pipeline.PipelineExecutionService;
import com.economicbriefing.pipeline.PipelineOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Hourly trigger. All execution logic lives in {@link PipelineExecutionService}, which the
 * admin API calls too — this class only decides <em>when</em>.
 */
@Component
@ConditionalOnProperty(name = "briefing.scheduler.enabled", havingValue = "true")
public class BriefingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BriefingScheduler.class);

    private final PipelineExecutionService executionService;

    public BriefingScheduler(PipelineExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * Invoked by the cron task {@link SchedulingConfig} registers — deliberately not annotated
     * with {@code @Scheduled}, so an invalid cron cannot abort application startup.
     *
     * <p>Runs on the scheduler thread so an overrun shows up as a skipped next tick rather than
     * as silent overlap. Never throws: an escaping exception would be logged by Spring as an
     * unhandled scheduling error, and this keeps the guarantee explicit instead of inherited.
     */
    public void runHourlyBriefing() {
        try {
            executionService.tryRun(PipelineOptions.hourly());
        } catch (Exception e) {
            log.error("[Scheduler] Tick failed; the next tick will still run", e);
        }
    }
}
