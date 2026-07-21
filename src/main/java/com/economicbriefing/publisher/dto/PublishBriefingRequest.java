package com.economicbriefing.publisher.dto;

import com.economicbriefing.domain.briefing.Briefing;

public record PublishBriefingRequest(
    Briefing briefing,
    boolean dryRun
) {}
