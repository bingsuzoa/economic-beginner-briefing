package com.economicbriefing.scheduler;

import java.time.ZoneId;

import com.economicbriefing.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

/**
 * Registers the hourly trigger programmatically instead of with {@code @Scheduled}.
 *
 * <p>{@code @Scheduled(cron = "...")} parses the expression while the bean is created, so a
 * typo in {@code briefing.scheduler.cron} aborted the whole application context — taking the
 * public briefing API and the reader-facing site down with it. Parsing here lets a bad cron
 * disable only the scheduler.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "briefing.scheduler.enabled", havingValue = "true")
public class SchedulingConfig implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final AppProperties appProperties;
    private final BriefingScheduler scheduler;

    public SchedulingConfig(AppProperties appProperties, BriefingScheduler scheduler) {
        this.appProperties = appProperties;
        this.scheduler = scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        AppProperties.SchedulerProperties props = appProperties.scheduler();

        if (!props.cronValid()) {
            // ASCII only: operational logs get read on consoles that mangle non-ASCII.
            log.error("[Scheduler] MISCONFIGURED cron='{}' - hourly briefings will NOT run. "
                            + "Expected 6 fields (sec min hour day month weekday), e.g. '0 0 * * * *'. "
                            + "The application keeps serving; fix briefing.scheduler.cron and restart.",
                    props.cron());
            return;
        }

        registrar.addCronTask(new CronTask(
                scheduler::runHourlyBriefing,
                new CronTrigger(props.cron(), ZONE)));
    }
}
