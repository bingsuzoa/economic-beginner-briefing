package com.economicbriefing.analyzer.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ArticleValidationResult(List<ArticleValidation> articles) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArticleValidation(String articleId, List<Finding> findings) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Finding(
            FindingType type,
            String issue,
            TargetType targetType,
            String targetReference,
            String description,
            JsonNode currentValue,
            JsonNode suggestedValue,
            String evidence) {}

    public enum FindingType {
        MISSING, WRONG_TYPE, WRONG_SPEAKER, UNSUPPORTED, INACCURATE
    }

    public enum TargetType {
        ISSUE, MAIN_FACT, CHANGE, RELATION, ARTICLE_EXPLANATION, STATEMENT, KEY_TERM
    }
}
