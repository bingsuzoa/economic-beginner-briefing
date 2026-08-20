package com.economicbriefing.analyzer.dto;

import java.util.List;

import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.domain.briefing.Briefing;

public record AnalyzeNewsResult(
    Briefing briefing,
    List<String> rejectedArticleIds,
    List<String> warnings,
    ArticleValidationResult validation,
    ArticleAnalysisResponse articleAnalysis,
    String analyzerModelName,
    String analyzerPromptVersion
) {
    public AnalyzeNewsResult(Briefing briefing, List<String> rejectedArticleIds, List<String> warnings) {
        this(briefing, rejectedArticleIds, warnings, null, null, null, null);
    }

    public AnalyzeNewsResult(
            Briefing briefing,
            List<String> rejectedArticleIds,
            List<String> warnings,
            ArticleValidationResult validation) {
        this(briefing, rejectedArticleIds, warnings, validation, null, null, null);
    }
}
