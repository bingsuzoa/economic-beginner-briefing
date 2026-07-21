package com.economicbriefing.collector.source;

import java.time.OffsetDateTime;
import java.util.List;

import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;

public interface SourceAdapter {

    String getSourceName();

    String getFeedUrl();

    ArticleSourceType getSourceType();

    List<NewsCategory> getDefaultCategories();

    SourceCollectionResult collect(OffsetDateTime startTime, OffsetDateTime endTime);

    record SourceCollectionResult(
        List<Article> articles,
        int collectedCount,
        int acceptedCount
    ) {}
}
