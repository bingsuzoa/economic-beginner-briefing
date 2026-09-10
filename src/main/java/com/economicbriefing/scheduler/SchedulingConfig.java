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

        if (props.collectCronValid()) {
            registrar.addCronTask(new CronTask(scheduler::collect, new CronTrigger(props.collectCron(), ZONE)));
        } else {
            log.error("[Scheduler] invalid collect-cron='{}'; collection disabled", props.collectCron());
        }
        if (props.dailyCronValid()) {
            registrar.addCronTask(new CronTask(scheduler::publishDaily, new CronTrigger(props.dailyCron(), ZONE)));
        } else {
            log.error("[Scheduler] invalid daily-cron='{}'; daily briefing disabled", props.dailyCron());
        }
    }
}
