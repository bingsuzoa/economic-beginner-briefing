package com.economicbriefing.exchangerate;

import java.time.LocalDate;

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
@ConditionalOnProperty(name = "exchange-rate.korea-exim.scheduler-enabled", havingValue = "true")
public class ExchangeRateSchedulingConfig implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateSchedulingConfig.class);
    private final ExchangeRateProperties properties;
    private final ExchangeRateService service;

    public ExchangeRateSchedulingConfig(ExchangeRateProperties properties, ExchangeRateService service) {
        this.properties = properties;
        this.service = service;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        if (!properties.schedulerCronValid()) {
            log.error("[ExchangeRate] Scheduler disabled: invalid cron='{}'", properties.schedulerCron());
            return;
        }
        registrar.addCronTask(new CronTask(() -> {
            LocalDate date = LocalDate.now(ExchangeRateService.KST);
            try {
                int inserted = service.collect(date);
                log.info("[ExchangeRate] Scheduler finished date={} inserted={}", date, inserted);
            } catch (Exception e) {
                log.error("[ExchangeRate] Scheduler failed date={}", date, e);
            }
        }, new CronTrigger(properties.schedulerCron(), ExchangeRateService.KST)));
    }
}
