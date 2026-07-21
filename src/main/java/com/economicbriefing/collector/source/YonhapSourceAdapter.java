package com.economicbriefing.collector.source;

import java.util.List;

import com.economicbriefing.collector.filter.CategoryClassifier;
import com.economicbriefing.collector.parser.ArticleNormalizer;
import com.economicbriefing.collector.parser.RssParser;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import org.springframework.stereotype.Component;

@Component
public class YonhapSourceAdapter extends AbstractRssSourceAdapter {

    public YonhapSourceAdapter(RssParser rssParser, ArticleNormalizer normalizer, CategoryClassifier categoryClassifier) {
        super(rssParser, normalizer, categoryClassifier);
    }

    @Override
    public String getSourceName() { return "연합뉴스"; }

    @Override
    public String getFeedUrl() { return "https://www.yna.co.kr/rss/economy.xml"; }

    @Override
    public ArticleSourceType getSourceType() { return ArticleSourceType.NEWS_MEDIA; }

    @Override
    public List<NewsCategory> getDefaultCategories() { return List.of(); }
}
