package com.economicbriefing.domain.analysis;

import java.util.List;

import com.economicbriefing.domain.article.NewsCategory;

public record AnalyzedNews(
    String id,
    String representativeTitle,
    NewsCategory category,
    int importance,
    String whyImportant,
    TargetAudience targetAudience,
    List<ImpactAssessment> impactAssessment,
    String oneLineSummary,
    String explanation,
    NewsEvidenceStatus evidenceStatus,
    String uncertaintyNote,
    List<EconomicTerm> economicTerms,
    List<SourceReference> sources
) {}
