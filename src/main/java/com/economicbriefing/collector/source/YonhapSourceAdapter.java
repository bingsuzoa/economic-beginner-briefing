package com.economicbriefing.collector.source;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.economicbriefing.collector.filter.CategoryClassifier;
import com.economicbriefing.collector.parser.ArticleNormalizer;
import com.economicbriefing.collector.parser.RssItem;
import com.economicbriefing.collector.parser.RssParser;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class YonhapSourceAdapter extends AbstractRssSourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(YonhapSourceAdapter.class);

    private static final List<String> FEED_URLS = List.of(
            "https://www.yna.co.kr/rss/economy.xml",
            "https://www.yna.co.kr/rss/politics.xml",
            "https://www.yna.co.kr/rss/society.xml",
            "https://www.yna.co.kr/rss/international.xml",
            "https://www.yna.co.kr/rss/industry.xml",
            "https://www.yna.co.kr/rss/culture.xml"
    );

    public YonhapSourceAdapter(RssParser rssParser, ArticleNormalizer normalizer, CategoryClassifier categoryClassifier) {
        super(rssParser, normalizer, categoryClassifier);
    }

    @Override
    public String getSourceName() { return "연합뉴스"; }

    @Override
    public String getFeedUrl() { return FEED_URLS.get(0); }

    @Override
    public ArticleSourceType getSourceType() { return ArticleSourceType.NEWS_MEDIA; }

    @Override
    public List<NewsCategory> getDefaultCategories() { return List.of(); }

    @Override
    public SourceCollectionResult collect(OffsetDateTime startTime, OffsetDateTime endTime) {
        List<Article> allArticles = new ArrayList<>();
        int totalCollected = 0;

        for (String feedUrl : FEED_URLS) {
            try {
                List<RssItem> items = rssParser.parse(feedUrl);
                totalCollected += items.size();
                for (RssItem item : items) {
                    Article article = parseItem(item, startTime, endTime);
                    if (article != null) {
                        allArticles.add(article);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to collect from {}: {}", feedUrl, e.getMessage());
            }
        }

        log.info("Yonhap collected {} articles from {} feeds (total fetched: {})",
                allArticles.size(), FEED_URLS.size(), totalCollected);
        return new SourceCollectionResult(allArticles, totalCollected, allArticles.size());
    }

    @Override
    protected List<NewsCategory> determineCategories(String title, String summary) {
        // Skip keyword-based classification — let GPT-4o decide relevance directly
        return List.of(NewsCategory.OTHER);
    }
}
