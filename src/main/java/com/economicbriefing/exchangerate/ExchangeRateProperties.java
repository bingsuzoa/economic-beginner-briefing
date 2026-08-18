package com.economicbriefing.exchangerate;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

@ConfigurationProperties(prefix = "exchange-rate.korea-exim")
public record ExchangeRateProperties(
        String apiKey,
        URI apiUrl,
        Duration timeout,
        boolean schedulerEnabled,
        String schedulerCron
) {
    public boolean schedulerCronValid() {
        return schedulerCron != null && CronExpression.isValidExpression(schedulerCron);
    }
}
