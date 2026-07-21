package com.economicbriefing.scheduler;

import com.economicbriefing.domain.execution.ExecutionLog;
import com.economicbriefing.pipeline.BriefingPipeline;
import com.economicbriefing.pipeline.PipelineOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BriefingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BriefingScheduler.class);

    private final BriefingPipeline pipeline;

    public BriefingScheduler(BriefingPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Scheduled(cron = "${briefing.scheduler.cron}")
    public void runHourlyBriefing() {
        log.info("Scheduled hourly briefing triggered");
        try {
            PipelineOptions options = PipelineOptions.hourly();
            ExecutionLog result = pipeline.run(options);
            log.info("Scheduled briefing completed: status={}, targetDate={}",
                    result.getStatus(), result.getTargetDate());
        } catch (Exception e) {
            log.error("Scheduled briefing failed with unexpected error", e);
        }
    }
}
