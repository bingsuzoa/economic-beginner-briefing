package com.economicbriefing.publisher.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PublishBriefingResult(
    String briefingId,
    List<PublishChannelResult> results,
    OffsetDateTime completedAt
) {}
