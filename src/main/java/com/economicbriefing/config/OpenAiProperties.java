package com.economicbriefing.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
    String apiKey,
    String model,
    double temperature,
    Duration timeout,
    int maxSelectedNews,
    String economicFlowComparisonModel,
    String economicFlowTraversalModel
) {
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public OpenAiProperties(String apiKey, String model, double temperature, Duration timeout,
            int maxSelectedNews, String economicFlowComparisonModel, String economicFlowTraversalModel) {
        this.apiKey = apiKey; this.model = model; this.temperature = temperature; this.timeout = timeout;
        this.maxSelectedNews = maxSelectedNews;
        this.economicFlowComparisonModel = economicFlowComparisonModel;
        this.economicFlowTraversalModel = economicFlowTraversalModel;
    }

    public OpenAiProperties(String apiKey, String model, double temperature,
            Duration timeout, int maxSelectedNews) {
        this(apiKey, model, temperature, timeout, maxSelectedNews, model, model);
    }
}
