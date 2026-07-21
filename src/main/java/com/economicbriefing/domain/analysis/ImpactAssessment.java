package com.economicbriefing.domain.analysis;

public record ImpactAssessment(
    String target,
    int score,
    String reason
) {}
