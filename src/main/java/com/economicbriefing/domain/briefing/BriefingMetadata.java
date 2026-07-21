package com.economicbriefing.domain.briefing;

public record BriefingMetadata(
    int collectedArticleCount,
    int analyzedArticleCount,
    int selectedNewsCount,
    String modelName,
    String promptVersion
) {}
