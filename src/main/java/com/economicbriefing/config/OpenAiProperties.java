package com.economicbriefing.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
    String apiKey,
    Duration timeout,
    String screeningModel,
    String extractionModel,
    String synthesisModel,
    String writingModel,
    String embeddingModel
) {}
