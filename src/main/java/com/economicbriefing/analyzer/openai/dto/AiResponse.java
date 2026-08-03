package com.economicbriefing.analyzer.openai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiResponse(
    @JsonProperty("overallSummary") List<String> overallSummary,
    @JsonProperty("news") List<AiAnalyzedNews> news,
    @JsonProperty("glossary") List<AiEconomicTerm> glossary
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiAnalyzedNews(
        @JsonProperty("id") String id,
        @JsonProperty("easyTitle") String easyTitle,
        @JsonProperty("category") String category,
        @JsonProperty("importance") int importance,
        @JsonProperty("threeLineSummary") List<String> threeLineSummary,
        @JsonProperty("whatHappened") String whatHappened,
        @JsonProperty("whyItHappened") String whyItHappened,
        @JsonProperty("beginnerExplanation") String beginnerExplanation,
        @JsonProperty("economicImpact") String economicImpact,
        @JsonProperty("terms") List<AiEconomicTerm> terms,
        @JsonProperty("evidenceStatus") String evidenceStatus
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiEconomicTerm(
        @JsonProperty("term") String term,
        @JsonProperty("explanation") String explanation,
        @JsonProperty("example") String example
    ) {}
}
