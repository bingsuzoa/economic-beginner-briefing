package com.economicbriefing.exchangerate;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "exchange-rate")
public record ExchangeRateProperties(Fixer fixer, Ecos ecos) {
    public record Fixer(String apiKey, URI apiUrl, Duration timeout, boolean schedulerEnabled, String schedulerCron) {}
    public record Ecos(String apiKey, URI apiUrl, Duration timeout, boolean schedulerEnabled, String schedulerCron) {}
}
