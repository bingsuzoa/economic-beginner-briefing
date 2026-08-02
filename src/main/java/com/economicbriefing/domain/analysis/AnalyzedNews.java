package com.economicbriefing.domain.analysis;

import java.util.List;

import com.economicbriefing.domain.article.NewsCategory;

public record AnalyzedNews(
    String id,
    String easyTitle,
    NewsCategory category,
    int importance,
    List<String> threeLineSummary,
    String whatHappened,
    String whyItHappened,
    String beginnerExplanation,
    String economicImpact,
    List<EconomicTerm> terms,
    NewsEvidenceStatus evidenceStatus,
    List<SourceReference> sources
) {}
