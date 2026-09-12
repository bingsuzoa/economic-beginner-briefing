package com.economicbriefing.exchangerate;

import java.time.OffsetDateTime;
import java.util.List;

public record ExchangeRateBriefingResponse(
        String currency, String articleId, String title, String explanation,
        List<Flow> flow, String source, OffsetDateTime publishedAt, String articleUrl) {
    public record Flow(String from, String to, String relationType) {}
}
