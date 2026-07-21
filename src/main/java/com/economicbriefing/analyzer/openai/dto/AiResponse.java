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
        @JsonProperty("representativeTitle") String representativeTitle,
        @JsonProperty("category") String category,
        @JsonProperty("importance") int importance,
        @JsonProperty("whyImportant") String whyImportant,
        @JsonProperty("oneLineSummary") String oneLineSummary,
        @JsonProperty("explanation") String explanation,
        @JsonProperty("evidenceStatus") String evidenceStatus,
        @JsonProperty("uncertaintyNote") String uncertaintyNote,
        @JsonProperty("economicTerms") List<AiEconomicTerm> economicTerms,
        @JsonProperty("sources") List<AiSourceReference> sources
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiEconomicTerm(
        @JsonProperty("term") String term,
        @JsonProperty("explanation") String explanation,
        @JsonProperty("example") String example
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiSourceReference(
        @JsonProperty("articleId") String articleId,
        @JsonProperty("isPrimary") boolean isPrimary
    ) {}
}
