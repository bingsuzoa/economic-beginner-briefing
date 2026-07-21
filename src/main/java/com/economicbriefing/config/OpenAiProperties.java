package com.economicbriefing.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
    String apiKey,
    String model,
    double temperature,
    Duration timeout,
    int maxSelectedNews
) {}
