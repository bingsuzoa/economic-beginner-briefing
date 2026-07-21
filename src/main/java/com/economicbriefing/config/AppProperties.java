package com.economicbriefing.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "briefing")
public record AppProperties(
    boolean dryRun,
    TimeoutProperties timeouts,
    RetryProperties retry,
    DiversityProperties diversity,
    AudienceProperties audience,
    SchedulerProperties scheduler
) {
    public record TimeoutProperties(
        Duration rssHttp,
        Duration aiApi,
        Duration notionApi,
        Duration emailApi
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
    ) {}
}
