package com.economicbriefing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

@ConfigurationProperties(prefix = "briefing")
public record AppProperties(
        TimeoutProperties timeouts,
        SchedulerProperties scheduler,
        BudgetProperties budget
) {
    public record TimeoutProperties(Duration rssHttp) {}

    public record SchedulerProperties(boolean enabled, String collectCron, String dailyCron) {
        public boolean collectCronValid() { return valid(collectCron); }
        public boolean dailyCronValid() { return valid(dailyCron); }
        public boolean valid() { return collectCronValid() && dailyCronValid(); }
        public String state() {
            if (!enabled) return "DISABLED";
            return valid() ? "ENABLED" : "MISCONFIGURED";
        }
        private static boolean valid(String value) {
            return value != null && CronExpression.isValidExpression(value);
        }
    }

    public record BudgetProperties(
            int screeningInputTokens,
            int extractionInputTokens,
            int synthesisInputTokens,
            double dailyCostUsd
    ) {}
}
