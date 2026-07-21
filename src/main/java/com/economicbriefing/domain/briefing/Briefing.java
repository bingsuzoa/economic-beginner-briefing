package com.economicbriefing.domain.briefing;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.economicbriefing.domain.analysis.AnalyzedNews;
import com.economicbriefing.domain.analysis.EconomicTerm;

public record Briefing(
    String id,
    LocalDate targetDate,
    OffsetDateTime generatedAt,
    String title,
    List<String> overallSummary,
    List<AnalyzedNews> news,
    List<EconomicTerm> glossary,
    BriefingMetadata metadata
) {}
