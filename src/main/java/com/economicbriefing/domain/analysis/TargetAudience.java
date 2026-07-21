package com.economicbriefing.domain.analysis;

import java.util.List;

public record TargetAudience(
    List<String> mustRead,
    List<String> notRelevant
) {}
