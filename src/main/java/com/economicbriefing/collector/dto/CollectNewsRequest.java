package com.economicbriefing.collector.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record CollectNewsRequest(
    LocalDate targetDate,
    ZoneId timezone,
    Integer maxArticles,
    OffsetDateTime startTime,
    OffsetDateTime endTime
) {
    public CollectNewsRequest {
        if (timezone == null) {
            timezone = ZoneId.of("Asia/Seoul");
        }
    }

    public static CollectNewsRequest of(LocalDate targetDate) {
        return new CollectNewsRequest(targetDate, ZoneId.of("Asia/Seoul"), null, null, null);
    }

    public static CollectNewsRequest of(LocalDate targetDate, OffsetDateTime startTime, OffsetDateTime endTime) {
        return new CollectNewsRequest(targetDate, ZoneId.of("Asia/Seoul"), null, startTime, endTime);
    }
}
