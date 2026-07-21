package com.economicbriefing.analyzer.dto;

import java.util.List;

import com.economicbriefing.domain.briefing.Briefing;

public record AnalyzeNewsResult(
    Briefing briefing,
    List<String> rejectedArticleIds,
    List<String> warnings
) {}
