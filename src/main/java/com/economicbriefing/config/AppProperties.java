package com.economicbriefing.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

@ConfigurationProperties(prefix = "briefing")
public record AppProperties(
    boolean dryRun,
    TimeoutProperties timeouts,
    RetryProperties retry,
    DiversityProperties diversity,
    AudienceProperties audience,
    SchedulerProperties scheduler,
    TeacherProperties teacher,
    EmbeddingProperties embedding
) {
    public record TimeoutProperties(
        Duration rssHttp,
        Duration aiApi
    ) {}

    public record RetryProperties(
        int maxAttempts,
        Duration initialDelay,
        Duration nextDelay
    ) {}

    public record DiversityProperties(
        int maxArticlesPerSource,
        int maxArticlesPerCategory,
        int minPersonalFinanceRelevance,
        int softMaxOverrideScore
    ) {}

    public record AudienceProperties(
        String economicKnowledgeLevel,
        List<String> interests,
        List<String> contextNotes
    ) {}

    public record SchedulerProperties(
        boolean enabled,
        String cron
    ) {
        /**
         * A bad cron must not take the whole service down — the public briefing API has
         * nothing to do with scheduling. Callers use this to skip registering the task
         * and report the misconfiguration instead.
         */
        public boolean cronValid() {
            return cron != null && CronExpression.isValidExpression(cron);
        }

        /** True only when the hourly trigger can actually be armed. */
        public boolean active() {
            return enabled && cronValid();
        }

        /** ENABLED / DISABLED / MISCONFIGURED — what operators see at boot and on /status. */
        public String state() {
            if (!enabled) return "DISABLED";
            return cronValid() ? "ENABLED" : "MISCONFIGURED";
        }
    }

    public record TeacherProperties(
        boolean enabled,
        String promptVersion,
        String model,
        int concurrency
    ) {}

    public record EmbeddingProperties(
        boolean enabled,
        String model,
        int dimensions
    ) {}
}
