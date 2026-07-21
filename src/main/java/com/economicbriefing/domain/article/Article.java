package com.economicbriefing.domain.article;

import java.time.OffsetDateTime;
import java.util.List;

public record Article(
    String id,
    String title,
    String summary,
    String sourceName,
    ArticleSourceType sourceType,
    OffsetDateTime publishedAt,
    OffsetDateTime collectedAt,
    String url,
    List<NewsCategory> categories,
    String language,
    String content
) {}
