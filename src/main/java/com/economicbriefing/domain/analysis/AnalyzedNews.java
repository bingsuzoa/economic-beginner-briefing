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
    String economicImpact,
    String householdImpact,
    List<String> affectedPeople,
    String positiveImpact,
    String negativeImpact,
    List<String> actionItems,
    List<EconomicTerm> terms,
    NewsEvidenceStatus evidenceStatus,
    List<String> uncertainties,
    List<SourceReference> sources
) {}
