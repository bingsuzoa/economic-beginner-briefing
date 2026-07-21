package com.economicbriefing.collector.source;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.economicbriefing.collector.filter.CategoryClassifier;
import com.economicbriefing.collector.parser.ArticleNormalizer;
import com.economicbriefing.collector.parser.RssItem;
import com.economicbriefing.collector.parser.RssParser;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.NewsCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractRssSourceAdapter implements SourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(AbstractRssSourceAdapter.class);

    protected final RssParser rssParser;
    protected final ArticleNormalizer normalizer;
    protected final CategoryClassifier categoryClassifier;

    protected AbstractRssSourceAdapter(RssParser rssParser, ArticleNormalizer normalizer, CategoryClassifier categoryClassifier) {
        this.rssParser = rssParser;
        this.normalizer = normalizer;
        this.categoryClassifier = categoryClassifier;
    }

    @Override
    public SourceCollectionResult collect(OffsetDateTime startTime, OffsetDateTime endTime) {
        List<Article> allArticles = new ArrayList<>();
        int totalCollected = 0;

        try {
            List<RssItem> items = rssParser.parse(getFeedUrl());
            totalCollected = items.size();

            for (RssItem item : items) {
                Article article = parseItem(item, startTime, endTime);
                if (article != null) {
                    allArticles.add(article);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to collect from {}: {}", getSourceName(), e.getMessage());
        }

        return new SourceCollectionResult(allArticles, totalCollected, allArticles.size());
    }

    protected Article parseItem(RssItem item, OffsetDateTime startTime, OffsetDateTime endTime) {
        if (item.publishedDate() == null) return null;

        OffsetDateTime publishedAt = item.publishedDate().toInstant().atOffset(ZoneOffset.ofHours(9));

        Instant publishedInstant = publishedAt.toInstant();
        if (publishedInstant.isBefore(startTime.toInstant()) || publishedInstant.isAfter(endTime.toInstant())) {
            return null;
        }

        String title = item.title();
        if (title == null || title.trim().isEmpty()) return null;
        title = title.trim();

        String url = item.link();
        if (url == null || url.trim().isEmpty()) return null;
        url = url.trim();

        String summary = item.description() != null ? stripHtml(item.description().trim()) : "";
        String content = item.content() != null ? stripHtml(item.content().trim()) : null;

        List<NewsCategory> categories = determineCategories(title, summary);
        if (categories.isEmpty()) return null;

        return normalizer.normalize(
                title, url, publishedAt, summary,
                getSourceName(), getSourceType(),
                categories, content, item.guid()
        );
    }

    protected List<NewsCategory> determineCategories(String title, String summary) {
        List<NewsCategory> classified = categoryClassifier.classify(title, summary);
        if (!classified.isEmpty()) return classified;

        List<NewsCategory> defaults = getDefaultCategories();
        if (defaults != null && !defaults.isEmpty()) return defaults;

        return List.of();
    }

    private String stripHtml(String html) {
        if (html == null) return null;
        return html.replaceAll("<[^>]*>", "").trim();
    }
}
