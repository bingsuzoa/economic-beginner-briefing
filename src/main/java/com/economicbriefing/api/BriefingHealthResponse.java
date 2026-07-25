package com.economicbriefing.api;

import java.util.List;

/**
 * Monitoring payload for {@code GET /api/health/briefing}.
 * The HTTP status carries the verdict (200 UP / 503 DOWN) so an uptime check needs no
 * JSON parsing; the body explains why for whoever gets paged.
 */
public record BriefingHealthResponse(
    String status,
    String scheduler,
    String cron,
    boolean dbConnected,
    String lastSuccessAt,
    Long lastSuccessAgeMinutes,
    List<String> reasons
) {}
