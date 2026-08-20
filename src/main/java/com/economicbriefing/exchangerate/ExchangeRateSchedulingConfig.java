package com.economicbriefing.exchangerate;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "exchange-rate.fixer.scheduler-enabled", havingValue = "true")
public class ExchangeRateSchedulingConfig {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateSchedulingConfig.class);
    private final ExchangeRateProperties properties;
    private final CurrentExchangeRateService currentService;
    private final ExchangeRateService historyService;

    public ExchangeRateSchedulingConfig(ExchangeRateProperties properties,
            CurrentExchangeRateService currentService, ExchangeRateService historyService) {
        this.properties = properties;
        this.currentService = currentService;
        this.historyService = historyService;
    }

    @Scheduled(cron = "${exchange-rate.fixer.scheduler-cron}", zone = "Asia/Seoul")
    void refreshCurrent() {
        if (!properties.fixer().schedulerEnabled()) return;
        try { log.info("[ExchangeRate] Fixer saved={}", currentService.refresh()); }
        catch (Exception e) { log.error("[ExchangeRate] Fixer failed; keeping last value", e); }
    }

    @Scheduled(cron = "${exchange-rate.ecos.scheduler-cron}", zone = "Asia/Seoul")
    void refreshHistory() {
        if (!properties.ecos().schedulerEnabled()) return;
        LocalDate today = LocalDate.now(ExchangeRateService.KST);
        try { log.info("[ExchangeRate] ECOS saved={}", historyService.collect(today.minusDays(7), today)); }
        catch (Exception e) { log.error("[ExchangeRate] ECOS failed; keeping history", e); }
    }
}
