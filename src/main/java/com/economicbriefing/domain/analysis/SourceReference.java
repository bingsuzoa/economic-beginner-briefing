package com.economicbriefing.domain.analysis;

import java.time.OffsetDateTime;

public record SourceReference(
    String articleId,
    String sourceName,
    String title,
    String url,
    OffsetDateTime publishedAt,
    boolean isPrimary
) {}
