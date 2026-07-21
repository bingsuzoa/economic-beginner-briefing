package com.economicbriefing.collector.filter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.economicbriefing.config.AppProperties;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.NewsCategory;
import org.springframework.stereotype.Component;

@Component
public class DiversitySelector {

    private final AppProperties appProperties;

    public DiversitySelector(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public DiversitySelectionResult select(
            List<Article> articles,
            List<RelevanceScorer.RelevanceScore> scores,
            DiversityOptions options
    ) {
        int maxPerSource = options.maxArticlesPerSource() != null
                ? options.maxArticlesPerSource()
                : appProperties.diversity().maxArticlesPerSource();
        int maxPerCategory = options.maxArticlesPerCategory() != null
                ? options.maxArticlesPerCategory()
                : appProperties.diversity().maxArticlesPerCategory();
        int minRelevance = options.minRelevanceScore() != null
                ? options.minRelevanceScore()
                : appProperties.diversity().minPersonalFinanceRelevance();
        int maxTotal = options.maxTotal() != null ? options.maxTotal() : Integer.MAX_VALUE;
        int softMaxOverride = options.softMaxOverrideScore() != null
                ? options.softMaxOverrideScore()
                : appProperties.diversity().softMaxOverrideScore();

        Map<String, Integer> scoreMap = new HashMap<>();
        for (RelevanceScorer.RelevanceScore s : scores) {
            scoreMap.put(s.articleId(), s.score());
        }

        // Sort by relevance score (descending), then by published time (descending)
        List<Article> sorted = new ArrayList<>(articles);
        sorted.sort(Comparator
                .<Article, Integer>comparing(a -> scoreMap.getOrDefault(a.id(), 0), Comparator.reverseOrder())
                .thenComparing(Article::publishedAt, Comparator.reverseOrder()));

        List<Article> selected = new ArrayList<>();
        List<Article> excluded = new ArrayList<>();
        Map<String, Integer> sourceCounts = new HashMap<>();
        Map<String, Integer> categoryCounts = new HashMap<>();

        int excludedBySourceLimit = 0;
        int excludedByCategoryLimit = 0;
        int excludedByRelevance = 0;

        for (Article article : sorted) {
            if (selected.size() >= maxTotal) {
                excluded.add(article);
                continue;
            }

            int relevance = scoreMap.getOrDefault(article.id(), 0);

            if (relevance < minRelevance) {
                excluded.add(article);
                excludedByRelevance++;
                continue;
            }

            // Check source limit (hard cap)
            int sourceCount = sourceCounts.getOrDefault(article.sourceName(), 0);
            if (sourceCount >= maxPerSource) {
                excluded.add(article);
                excludedBySourceLimit++;
                continue;
            }

            // Check category limit (soft cap)
            List<NewsCategory> articleCategories = (article.categories() != null && !article.categories().isEmpty())
                    ? article.categories()
                    : List.of(NewsCategory.OTHER);

            boolean allCategoriesFull = articleCategories.stream().allMatch(cat -> {
                int count = categoryCounts.getOrDefault(cat.toValue(), 0);
                int effectiveMax = relevance >= softMaxOverride ? maxPerCategory + 1 : maxPerCategory;
                return count >= effectiveMax;
            });

            if (allCategoriesFull) {
                excluded.add(article);
                excludedByCategoryLimit++;
                continue;
            }

            selected.add(article);
            sourceCounts.merge(article.sourceName(), 1, Integer::sum);
            for (NewsCategory cat : articleCategories) {
                categoryCounts.merge(cat.toValue(), 1, Integer::sum);
            }
        }

        DiversityStats stats = new DiversityStats(
                new HashMap<>(sourceCounts), new HashMap<>(categoryCounts),
                excludedBySourceLimit, excludedByCategoryLimit, excludedByRelevance
        );

        return new DiversitySelectionResult(selected, excluded, stats);
    }

    public record DiversityOptions(
            Integer maxArticlesPerSource,
            Integer maxArticlesPerCategory,
            Integer minRelevanceScore,
            Integer maxTotal,
            Integer softMaxOverrideScore
    ) {
        public static DiversityOptions defaults() {
            return new DiversityOptions(null, null, null, null, null);
        }
    }

    public record DiversitySelectionResult(
            List<Article> selected,
            List<Article> excluded,
            DiversityStats stats
    ) {}

    public record DiversityStats(
            Map<String, Integer> bySource,
            Map<String, Integer> byCategory,
            int excludedBySourceLimit,
            int excludedByCategoryLimit,
            int excludedByRelevance
    ) {}
}
