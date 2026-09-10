package com.economicbriefing.article;

import com.economicbriefing.collector.parser.RssItem;
import com.economicbriefing.collector.parser.RssParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class YonhapArticleService {
    private static final Logger log = LoggerFactory.getLogger(YonhapArticleService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Pattern STORY_KEY = Pattern.compile("AKR\\d{12}");
    private static final List<String> FEEDS = List.of(
            "https://www.yna.co.kr/rss/economy.xml",
            "https://www.yna.co.kr/rss/politics.xml",
            "https://www.yna.co.kr/rss/society.xml",
            "https://www.yna.co.kr/rss/international.xml",
            "https://www.yna.co.kr/rss/industry.xml",
            "https://www.yna.co.kr/rss/market.xml",
            "https://www.yna.co.kr/rss/culture.xml");

    private final RssParser rss;
    private final ArticleRepository articles;

    public YonhapArticleService(RssParser rss, ArticleRepository articles) {
        this.rss = rss;
        this.articles = articles;
    }

    public int collectRecent() {
        OffsetDateTime end = OffsetDateTime.now(KST);
        return collect(end.minusHours(2), end);
    }

    public int collect(OffsetDateTime start, OffsetDateTime end) {
        Map<String, RssItem> latest = new LinkedHashMap<>();
        int successfulFeeds = 0;
        for (String feed : FEEDS) {
            try {
                List<RssItem> items = rss.parse(feed);
                successfulFeeds++;
                for (RssItem item : items) {
                    if (item.publishedDate() == null || item.link() == null || item.title() == null) continue;
                    OffsetDateTime published = item.publishedDate().toInstant().atZone(KST).toOffsetDateTime();
                    if (published.isBefore(start) || !published.isBefore(end)) continue;
                    String key = storyKey(item.link(), item.guid());
                    latest.merge(key, item, (left, right) -> published(left).isAfter(published(right)) ? left : right);
                }
            } catch (RuntimeException e) {
                log.warn("Yonhap feed failed: feed={}, reason={}", feed, e.getMessage());
            }
        }
        if (successfulFeeds == 0) throw new IllegalStateException("all Yonhap feeds failed");

        int changed = 0;
        for (var entry : latest.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparing(YonhapArticleService::published))).toList()) {
            RssItem item = entry.getValue();
            String key = entry.getKey();
            ArticleEntity article = articles.findBySourceAndSourceArticleId("연합뉴스", key)
                    .or(() -> articles.findByUrl(item.link())).orElseGet(ArticleEntity::new);
            boolean fresh = article.getId() == null;
            if (fresh) article.setId(idFor(key));
            article.setSource("연합뉴스");
            article.setSourceArticleId(key);
            article.setTitle(clean(item.title()));
            article.setSummary(clean(stripHtml(item.description())));
            article.setUrl(item.link().strip());
            article.setPublishedAt(published(item));
            article.setCollectedAt(OffsetDateTime.now(KST));
            article.setContentHash(hash(article.getTitle() + "|" + article.getSummary()));
            articles.save(article);
            changed++;
        }
        log.info("Yonhap collection completed: feeds={}, candidates={}, savedOrUpdated={}", successfulFeeds, latest.size(), changed);
        return changed;
    }

    static String storyKey(String url, String guid) {
        Matcher matcher = STORY_KEY.matcher((url == null ? "" : url) + " " + (guid == null ? "" : guid));
        return matcher.find() ? matcher.group() : hash(url == null ? guid : url).substring(0, 24);
    }

    private static OffsetDateTime published(RssItem item) {
        return item.publishedDate().toInstant().atZone(KST).toOffsetDateTime();
    }

    private static String idFor(String key) {
        String id = "YONHAP:" + key;
        return id.length() <= 64 ? id : "YONHAP:" + hash(key).substring(0, 32);
    }

    private static String stripHtml(String value) {
        return value == null ? "" : value.replaceAll("<[^>]+>", " ");
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace("&quot;", "\"").replace("&#34;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
                .replaceAll("\\s+", " ").strip();
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
