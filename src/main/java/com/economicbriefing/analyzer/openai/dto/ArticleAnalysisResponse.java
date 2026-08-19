package com.economicbriefing.analyzer.openai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ArticleAnalysisResponse(List<ArticleAnalysis> articles) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArticleAnalysis(
            String articleId,
            List<Issue> issues) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Issue(
            String name,
            List<String> mainFacts,
            List<Change> changes,
            List<Relation> relations,
            List<Statement> statements,
            List<String> keyTerms) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Change(String target, String before, String after, ChangeStatus status) {}

    public enum ChangeStatus {
        CONFIRMED, PROPOSED, EXPECTED
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Relation(
            String from,
            String to,
            RelationType relationType,
            String articleExplanation,
            StatementType evidenceType,
            String speaker) {}

    public enum RelationType {
        CAUSE_OR_RESULT,
        PURPOSE,
        CHANGE,
        COMPARISON,
        CONDITION,
        ASSOCIATION,
        CLAIMED_EFFECT,
        EXPECTED_EFFECT,
        NEXT_STEP,
        EXPECTED_PROCESS
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Statement(StatementType type, String speaker, String content) {}

    public enum StatementType {
        FACT, CLAIM, INTERPRETATION, PREDICTION, PROPOSAL, PLAN
    }
}
