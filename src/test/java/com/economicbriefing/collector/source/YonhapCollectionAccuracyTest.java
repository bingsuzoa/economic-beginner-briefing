package com.economicbriefing.collector.source;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.economicbriefing.collector.filter.CategoryClassifier;
import com.economicbriefing.collector.filter.DateFilter;
import com.economicbriefing.collector.filter.DuplicateRemover;
import com.economicbriefing.collector.filter.QualityValidator;
import com.economicbriefing.collector.parser.ArticleNormalizer;
import com.economicbriefing.collector.parser.RssItem;
import com.economicbriefing.collector.parser.RssParser;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.domain.article.Article;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Manual live probe for the fixed 2026-08-14 19:00~20:00 KST window.
 * It deliberately creates no Spring context, so Scheduler, DB and OpenAI cannot run.
 */
@EnabledIfEnvironmentVariable(named = "YONHAP_LIVE_TEST", matches = "true")
class YonhapCollectionAccuracyTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-08-14T19:00:00+09:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-08-14T20:00:00+09:00");
    private static final java.nio.file.Path OUTPUT = java.nio.file.Path.of(
            "pipeline-debug", "yonhap-20260814-1900");

    // Snapshot of YonhapSourceAdapter.FEED_URLS. Kept here so production visibility/API is unchanged.
    private static final Map<String, String> FEEDS = Map.ofEntries(
            Map.entry("economy", "https://www.yna.co.kr/rss/economy.xml"),
            Map.entry("politics", "https://www.yna.co.kr/rss/politics.xml"),
            Map.entry("society", "https://www.yna.co.kr/rss/society.xml"),
            Map.entry("international", "https://www.yna.co.kr/rss/international.xml"),
            Map.entry("industry", "https://www.yna.co.kr/rss/industry.xml"),
            Map.entry("market", "https://www.yna.co.kr/rss/market.xml"),
            Map.entry("culture", "https://www.yna.co.kr/rss/culture.xml")
    );

    private static final List<String> AD_KEYWORDS = List.of(
            "[AD]", "[광고]", "[제휴]", "[이벤트]", "[홍보]",
            "광고", "제휴", "이벤트 안내", "협찬");

    @Test
    void capturesLiveYonhapCollectionFunnel() throws Exception {
        RssParser parser = new RssParser(properties());
        InspectableYonhapAdapter adapter = new InspectableYonhapAdapter(
                parser, new ArticleNormalizer(), new CategoryClassifier());
        DateFilter dateFilter = new DateFilter();
        QualityValidator qualityValidator = new QualityValidator();
        DuplicateRemover duplicateRemover = new DuplicateRemover();
        ObjectMapper json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);

        java.nio.file.Files.createDirectories(OUTPUT);
        List<FeedFetch> feedFetches = new ArrayList<>();
        List<FeedArticle> created = new ArrayList<>();
        List<RssRejection> articleRejections = new ArrayList<>();
        IdentityHashMap<Article, String> articleFeeds = new IdentityHashMap<>();

        for (Map.Entry<String, String> feed : FEEDS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            List<RssItem> items;
            String error = null;
            try {
                items = parser.parse(feed.getValue());
            } catch (RuntimeException e) {
                items = List.of();
                error = e.getClass().getSimpleName() + ": " + e.getMessage();
            }

            feedFetches.add(new FeedFetch(feed.getKey(), feed.getValue(), items.size(), error,
                    items.stream().map(item -> rssView(feed.getKey(), item)).toList()));

            for (RssItem item : items) {
                Article article = adapter.parseForTest(item, START, END);
                if (article == null) {
                    articleRejections.add(new RssRejection(
                            feed.getKey(), item.title(), item.link(), publishedAt(item),
                            parseRejectionReason(item)));
                } else {
                    articleFeeds.put(article, feed.getKey());
                    created.add(new FeedArticle(feed.getKey(), article));
                }
            }
        }

        List<Article> articles = created.stream().map(FeedArticle::article).toList();
        DateFilter.DateFilterResult date = dateFilter.filter(articles, START, END);
        QualityValidator.QualityValidationResult quality = qualityValidator.validate(date.accepted());
        DuplicateRemover.DeduplicationResult dedupe = duplicateRemover.removeDuplicates(quality.valid());
        List<DedupTrace> dedupTraces = traceDuplicates(quality.valid(), dedupe, articleFeeds);

        assertEquals(articles.size(), date.accepted().size() + date.rejected().size());
        assertEquals(date.accepted().size(), quality.valid().size() + quality.invalid().size());
        assertEquals(quality.valid().size(), dedupe.unique().size() + dedupe.duplicates().size());
        assertEquals(dedupe.duplicates().size(), dedupTraces.size(),
                "debug trace must describe every duplicate removed by production logic");

        write(json, "01-rss-items.json", Map.of(
                "range", range(),
                "feeds", feedFetches));
        write(json, "02-articles.json", Map.of(
                "inputRssItemCount", feedFetches.stream().mapToInt(FeedFetch::fetchedCount).sum(),
                "acceptedArticleCount", created.size(),
                "rejectedCount", articleRejections.size(),
                "accepted", created.stream().map(a -> articleView(a.feed(), a.article())).toList(),
                "rejected", articleRejections));
        write(json, "03-date-filter.json", Map.of(
                "boundary", "inclusive: start <= publishedAt <= end",
                "beforeCount", articles.size(),
                "acceptedCount", date.accepted().size(),
                "rejectedCount", date.rejected().size(),
                "accepted", views(date.accepted(), articleFeeds),
                "rejected", date.rejected().stream().map(a -> new ArticleRejection(
                        articleFeeds.get(a), articleView(articleFeeds.get(a), a), dateReason(a))).toList()));
        write(json, "04-quality-filter.json", Map.of(
                "beforeCount", date.accepted().size(),
                "validCount", quality.valid().size(),
                "invalidCount", quality.invalid().size(),
                "valid", views(quality.valid(), articleFeeds),
                "invalid", quality.invalid().stream().map(a -> new ArticleRejection(
                        articleFeeds.get(a), articleView(articleFeeds.get(a), a), qualityReason(a))).toList()));
        write(json, "05-deduplication.json", Map.of(
                "beforeCount", quality.valid().size(),
                "uniqueCount", dedupe.unique().size(),
                "duplicateCount", dedupe.duplicates().size(),
                "duplicates", dedupTraces));
        write(json, "06-final-articles.json", Map.of(
                "count", dedupe.unique().size(),
                "articles", views(dedupe.unique(), articleFeeds)));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", "YONHAP COLLECTION TEST");
        summary.put("range", range());
        summary.put("dateBoundary", "inclusive: start <= publishedAt <= end");
        summary.put("RSS_FETCHED", feedFetches.stream().mapToInt(FeedFetch::fetchedCount).sum());
        summary.put("ARTICLE_CREATED", created.size());
        summary.put("ARTICLE_REJECTED", articleRejections.size());
        summary.put("DATE_FILTER_INPUT", articles.size());
        summary.put("DATE_FILTER_ACCEPTED", date.accepted().size());
        summary.put("DATE_FILTER_REJECTED", date.rejected().size());
        summary.put("QUALITY_INPUT", date.accepted().size());
        summary.put("QUALITY_VALID", quality.valid().size());
        summary.put("QUALITY_INVALID", quality.invalid().size());
        summary.put("DEDUP_INPUT", quality.valid().size());
        summary.put("DEDUP_UNIQUE", dedupe.unique().size());
        summary.put("DEDUP_DUPLICATES", dedupe.duplicates().size());
        summary.put("FINAL", dedupe.unique().size());
        write(json, "summary.json", summary);

        System.out.printf("[YONHAP COLLECTION TEST] RSS=%d ARTICLE=%d FINAL=%d output=%s%n",
                feedFetches.stream().mapToInt(FeedFetch::fetchedCount).sum(),
                created.size(), dedupe.unique().size(), OUTPUT.toAbsolutePath());
    }

    private static AppProperties properties() {
        return new AppProperties(true,
                new AppProperties.TimeoutProperties(Duration.ofSeconds(10), Duration.ofSeconds(60)),
                new AppProperties.RetryProperties(1, Duration.ZERO, Duration.ZERO),
                new AppProperties.DiversityProperties(3, 3, 3, 5),
                new AppProperties.AudienceProperties("beginner", List.of(), List.of()),
                new AppProperties.SchedulerProperties(false, "0 0 * * * *"),
                new AppProperties.TeacherProperties(false, "teacher-v1", "gpt-4o-mini", 1),
                new AppProperties.EmbeddingProperties(false, "text-embedding-3-small", 1536));
    }

    private static Map<String, String> range() {
        return Map.of("start", START.toString(), "end", END.toString());
    }

    private static RssItemView rssView(String feed, RssItem item) {
        return new RssItemView(feed, item.title(), item.link(), publishedAt(item));
    }

    private static OffsetDateTime publishedAt(RssItem item) {
        Date date = item.publishedDate();
        return date == null ? null : date.toInstant().atOffset(ZoneOffset.ofHours(9));
    }

    private static String parseRejectionReason(RssItem item) {
        if (item.publishedDate() == null) return "MISSING_PUBLISHED_DATE";
        OffsetDateTime publishedAt = publishedAt(item);
        if (publishedAt.toInstant().isBefore(START.toInstant())) return "BEFORE_START";
        if (publishedAt.toInstant().isAfter(END.toInstant())) return "AFTER_END";
        if (item.title() == null || item.title().trim().isEmpty()) return "MISSING_TITLE";
        if (item.link() == null || item.link().trim().isEmpty()) return "MISSING_URL";
        return "EMPTY_CATEGORY";
    }

    private static String dateReason(Article article) {
        if (article.publishedAt() == null) return "MISSING_PUBLISHED_DATE";
        if (article.publishedAt().isBefore(START)) return "BEFORE_START";
        if (article.publishedAt().isAfter(END)) return "AFTER_END";
        return "UNKNOWN";
    }

    private static String qualityReason(Article article) {
        if (article.title() == null || article.title().trim().length() < 5) return "TITLE_TOO_SHORT";
        if (article.url() == null || !validHttpUrl(article.url())) return "INVALID_URL";
        if (article.publishedAt() == null) return "MISSING_PUBLISHED_DATE";
        if (AD_KEYWORDS.stream().anyMatch(article.title()::contains)) return "AD_KEYWORD";
        return "UNKNOWN";
    }

    private static boolean validHttpUrl(String value) {
        try {
            String scheme = URI.create(value).getScheme();
            return "http".equals(scheme) || "https".equals(scheme);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static List<ArticleView> views(List<Article> articles, IdentityHashMap<Article, String> feeds) {
        return articles.stream().map(a -> articleView(feeds.get(a), a)).toList();
    }

    private static ArticleView articleView(String feed, Article article) {
        return new ArticleView(feed, article.id(), article.title(), article.url(),
                article.publishedAt(), article.sourceName());
    }

    private static void write(ObjectMapper json, String filename, Object value) throws Exception {
        json.writeValue(OUTPUT.resolve(filename).toFile(), value);
    }

    /** Replays only the decisions for reporting, then asserts parity with the real result. */
    private static List<DedupTrace> traceDuplicates(
            List<Article> input, DuplicateRemover.DeduplicationResult result,
            IdentityHashMap<Article, String> feeds) throws Exception {
        Method normalizeUrl = DuplicateRemover.class.getDeclaredMethod("normalizeUrl", String.class);
        Method cleanTitle = DuplicateRemover.class.getDeclaredMethod("cleanTitleText", String.class);
        Method similarity = DuplicateRemover.class.getDeclaredMethod(
                "calculateSimilarity", String.class, String.class);
        Field thresholdField = DuplicateRemover.class.getDeclaredField("TITLE_SIMILARITY_THRESHOLD");
        normalizeUrl.setAccessible(true);
        cleanTitle.setAccessible(true);
        similarity.setAccessible(true);
        thresholdField.setAccessible(true);
        double threshold = thresholdField.getDouble(null);

        List<DedupTrace> traces = new ArrayList<>();
        Map<String, Article> byId = new HashMap<>();
        Map<String, Article> byUrl = new HashMap<>();
        Map<String, List<Article>> bySource = new HashMap<>();
        Set<Article> phaseOneDuplicates = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

        for (Article article : input) {
            String normalized = (String) normalizeUrl.invoke(null, article.url());
            Article representative = byId.get(article.id());
            String reason = "SAME_ID";
            Double score = null;
            if (representative == null) {
                representative = byUrl.get(normalized);
                reason = "SAME_NORMALIZED_URL";
            }
            if (representative == null) {
                String candidate = (String) cleanTitle.invoke(null, article.title());
                for (Article existing : bySource.getOrDefault(article.sourceName(), List.of())) {
                    String existingTitle = (String) cleanTitle.invoke(null, existing.title());
                    double calculated = (double) similarity.invoke(null, candidate, existingTitle);
                    if (calculated > threshold) {
                        representative = existing;
                        reason = "SAME_SOURCE_SIMILAR_TITLE";
                        score = calculated;
                        break;
                    }
                }
            }
            if (representative != null) {
                phaseOneDuplicates.add(article);
                traces.add(trace(representative, article, reason, score, feeds));
                continue;
            }
            byId.put(article.id(), article);
            byUrl.put(normalized, article);
            bySource.computeIfAbsent(article.sourceName(), ignored -> new ArrayList<>()).add(article);
        }

        for (DuplicateRemover.NewsEventGroup group : result.eventGroups()) {
            for (Article duplicate : group.relatedArticles()) {
                if (!phaseOneDuplicates.contains(duplicate)) {
                    String a = (String) cleanTitle.invoke(null, group.representative().title());
                    String b = (String) cleanTitle.invoke(null, duplicate.title());
                    traces.add(trace(group.representative(), duplicate,
                            "CROSS_SOURCE_SAME_EVENT", (double) similarity.invoke(null, a, b), feeds));
                }
            }
        }
        return traces;
    }

    private static DedupTrace trace(
            Article representative, Article duplicate, String reason, Double similarity,
            IdentityHashMap<Article, String> feeds) {
        return new DedupTrace(articleView(feeds.get(representative), representative),
                articleView(feeds.get(duplicate), duplicate), reason, similarity);
    }

    private static class InspectableYonhapAdapter extends YonhapSourceAdapter {
        InspectableYonhapAdapter(RssParser parser, ArticleNormalizer normalizer, CategoryClassifier classifier) {
            super(parser, normalizer, classifier);
        }

        Article parseForTest(RssItem item, OffsetDateTime start, OffsetDateTime end) {
            return parseItem(item, start, end);
        }
    }

    private record FeedArticle(String feed, Article article) {}
    private record FeedFetch(String feed, String url, int fetchedCount, String error, List<RssItemView> items) {}
    private record RssItemView(String feed, String title, String url, OffsetDateTime publishedAt) {}
    private record RssRejection(String feed, String title, String url,
                                OffsetDateTime publishedAt, String reason) {}
    private record ArticleView(String feed, String id, String title, String url,
                               OffsetDateTime publishedAt, String source) {}
    private record ArticleRejection(String feed, ArticleView article, String reason) {}
    private record DedupTrace(ArticleView representative, ArticleView duplicate,
                              String reason, Double similarity) {}
}
