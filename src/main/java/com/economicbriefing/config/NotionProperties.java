package com.economicbriefing.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notion")
public record NotionProperties(
    String apiKey,
    String databaseId,
    Duration timeout
) {}
