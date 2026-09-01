package com.economicbriefing.analyzer.dto;

import java.util.List;

import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.analyzer.openai.dto.RetrievalRouterResponse;
import com.economicbriefing.domain.briefing.Briefing;
import com.economicbriefing.economicflow.EventCandidate;

public record AnalyzeNewsResult(
    Briefing briefing,
    List<String> rejectedArticleIds,
    List<String> warnings,
    ArticleValidationResult validation,
    ArticleAnalysisResponse articleAnalysis,
    RetrievalRouterResponse routerResult,
    List<EventCandidate> eventCandidates,
    List<com.economicbriefing.economicflow.EventRelationCandidate> eventRelations
) {
    public AnalyzeNewsResult(Briefing briefing, List<String> rejectedArticleIds, List<String> warnings) {
        this(briefing, rejectedArticleIds, warnings, null, null, null, List.of(), List.of());
    }

    public AnalyzeNewsResult(
            Briefing briefing,
            List<String> rejectedArticleIds,
            List<String> warnings,
            ArticleValidationResult validation) {
        this(briefing, rejectedArticleIds, warnings, validation, null, null, List.of(), List.of());
    }

    public AnalyzeNewsResult(
            Briefing briefing,
            List<String> rejectedArticleIds,
            List<String> warnings,
            ArticleValidationResult validation,
            ArticleAnalysisResponse articleAnalysis) {
        this(briefing, rejectedArticleIds, warnings, validation, articleAnalysis, null, List.of(), List.of());
    }

    public AnalyzeNewsResult(Briefing briefing, List<String> rejectedArticleIds, List<String> warnings,
            ArticleValidationResult validation, ArticleAnalysisResponse articleAnalysis,
            RetrievalRouterResponse routerResult) {
        this(briefing, rejectedArticleIds, warnings, validation, articleAnalysis, routerResult, List.of(), List.of());
    }
}
