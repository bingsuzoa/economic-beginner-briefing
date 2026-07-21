package com.economicbriefing.collector.filter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.economicbriefing.domain.article.Article;
import org.springframework.stereotype.Component;

@Component
public class DuplicateRemover {

    private static final double TITLE_SIMILARITY_THRESHOLD = 0.8;

    private static final List<String> TRACKING_PARAMS = List.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "ref", "fbclid", "gclid", "mc_cid", "mc_eid"
    );

    private static final Pattern BRACKET_PATTERN = Pattern.compile("\\[.*?]");
    private static final Pattern PAREN_PATTERN = Pattern.compile("\\(.*?\\)");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern CLICKBAIT_PATTERN = Pattern.compile("[!?]{2,}|충격|경악|헐|대박");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d");

    public DeduplicationResult removeDuplicates(List<Article> articles) {
        List<Article> duplicates = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        Set<String> seenIds = new HashSet<>();
        Map<String, List<String>> seenTitlesBySource = new HashMap<>();

        // Phase 1: Remove exact duplicates
        List<Article> afterExactDedup = new ArrayList<>();

        for (Article article : articles) {
            String normalizedUrl = normalizeUrl(article.url());

            if (seenIds.contains(article.id())) {
                duplicates.add(article);
                continue;
            }

            if (seenUrls.contains(normalizedUrl)) {
                duplicates.add(article);
                continue;
            }

            List<String> sourceTitles = seenTitlesBySource.computeIfAbsent(
                    article.sourceName(), k -> new ArrayList<>());
            if (isTitleDuplicate(article.title(), sourceTitles)) {
                duplicates.add(article);
                continue;
            }

            seenIds.add(article.id());
            seenUrls.add(normalizedUrl);
            sourceTitles.add(article.title());
            afterExactDedup.add(article);
        }

        // Phase 2: Group cross-source articles covering the same event
        List<NewsEventGroup> eventGroups = groupByEvent(afterExactDedup);

        List<Article> unique = new ArrayList<>();
        for (NewsEventGroup group : eventGroups) {
            unique.add(group.representative());
            duplicates.addAll(group.relatedArticles());
        }

        return new DeduplicationResult(unique, duplicates, eventGroups);
    }

    private List<NewsEventGroup> groupByEvent(List<Article> articles) {
        List<NewsEventGroup> groups = new ArrayList<>();
        Set<Integer> assigned = new HashSet<>();

        for (int i = 0; i < articles.size(); i++) {
            if (assigned.contains(i)) continue;

            Article articleI = articles.get(i);
            List<Article> group = new ArrayList<>();
            group.add(articleI);
            assigned.add(i);

            for (int j = i + 1; j < articles.size(); j++) {
                if (assigned.contains(j)) continue;

                Article articleJ = articles.get(j);

                // Only group articles from different sources
                if (articleI.sourceName().equals(articleJ.sourceName())) continue;

                String titleA = cleanTitleText(articleI.title());
                String titleB = cleanTitleText(articleJ.title());

                if (calculateSimilarity(titleA, titleB) > TITLE_SIMILARITY_THRESHOLD) {
                    group.add(articleJ);
                    assigned.add(j);
                }
            }

            if (group.size() == 1) {
                groups.add(new NewsEventGroup(articleI, List.of(), List.of(articleI.sourceName())));
            } else {
                group.sort(Comparator.comparingDouble(this::scoreArticleQuality).reversed());
                Article representative = group.get(0);
                List<Article> related = group.subList(1, group.size());
                List<String> sourceNames = group.stream().map(Article::sourceName).toList();
                groups.add(new NewsEventGroup(representative, new ArrayList<>(related), sourceNames));
            }
        }

        return groups;
    }

    private double scoreArticleQuality(Article article) {
        double score = 0;

        int summaryLength = article.summary() != null ? article.summary().length() : 0;
        score += Math.min(summaryLength / 50.0, 5.0);

        if (article.content() != null) {
            score += Math.min(article.content().length() / 200.0, 3.0);
        }

        if (DIGIT_PATTERN.matcher(article.title()).find()) {
            score += 2;
        }

        if (CLICKBAIT_PATTERN.matcher(article.title()).find()) {
            score -= 3;
        }

        if (article.title().length() < 15) {
            score -= 1;
        }

        return score;
    }

    static String normalizeUrl(String url) {
        try {
            URI parsed = URI.create(url);
            String query = parsed.getQuery();
            String cleanQuery = null;

            if (query != null) {
                StringBuilder sb = new StringBuilder();
                for (String param : query.split("&")) {
                    String key = param.contains("=") ? param.substring(0, param.indexOf('=')) : param;
                    if (!TRACKING_PARAMS.contains(key)) {
                        if (!sb.isEmpty()) sb.append('&');
                        sb.append(param);
                    }
                }
                cleanQuery = sb.isEmpty() ? null : sb.toString();
            }

            String host = parsed.getHost();
            if (host != null) {
                host = host.replaceFirst("^m\\.", "www.");
                host = host.replaceFirst("^mobile\\.", "www.");
            }

            URI normalized = new URI(
                    parsed.getScheme(), parsed.getUserInfo(), host,
                    parsed.getPort(), parsed.getPath(), cleanQuery, null
            );
            return normalized.toString();
        } catch (Exception e) {
            return url;
        }
    }

    private boolean isTitleDuplicate(String title, List<String> existingTitles) {
        String cleanTitle = cleanTitleText(title);
        for (String existing : existingTitles) {
            String cleanExisting = cleanTitleText(existing);
            if (calculateSimilarity(cleanTitle, cleanExisting) > TITLE_SIMILARITY_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    static String cleanTitleText(String title) {
        String cleaned = BRACKET_PATTERN.matcher(title).replaceAll("");
        cleaned = PAREN_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = WHITESPACE_PATTERN.matcher(cleaned).replaceAll("");
        return cleaned.trim();
    }

    static double calculateSimilarity(String a, String b) {
        if (a.equals(b)) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        String longer = a.length() >= b.length() ? a : b;
        String shorter = a.length() >= b.length() ? b : a;

        int[] longerChars = longer.codePoints().toArray();
        int[] shorterChars = shorter.codePoints().toArray();
        boolean[] used = new boolean[longerChars.length];

        int matchCount = 0;
        for (int ch : shorterChars) {
            for (int j = 0; j < longerChars.length; j++) {
                if (!used[j] && longerChars[j] == ch) {
                    matchCount++;
                    used[j] = true;
                    break;
                }
            }
        }

        return (matchCount * 2.0) / (longerChars.length + shorterChars.length);
    }

    public record DeduplicationResult(
            List<Article> unique,
            List<Article> duplicates,
            List<NewsEventGroup> eventGroups
    ) {}

    public record NewsEventGroup(
            Article representative,
            List<Article> relatedArticles,
            List<String> sourceNames
    ) {}
}
