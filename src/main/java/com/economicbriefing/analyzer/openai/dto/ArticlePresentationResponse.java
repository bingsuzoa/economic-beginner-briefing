package com.economicbriefing.analyzer.openai.dto;

import java.util.List;

public record ArticlePresentationResponse(List<ArticlePresentation> articles) {
    public record ArticlePresentation(String articleId, String displayTitle, List<String> summary,
                                      String whatHappened, List<WhyExplanation> whyExplanations) {}
    public record WhyExplanation(String requestId, String question, String explanation,
                                 ExplanationKind explanationKind, List<String> usedPrincipleChunkIds) {}
    public enum ExplanationKind { GENERAL_PRINCIPLE, ARTICLE_EVIDENCE }
}
