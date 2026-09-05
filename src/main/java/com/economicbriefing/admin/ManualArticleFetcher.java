package com.economicbriefing.admin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.economicbriefing.analyzer.openai.ArticleBodyFetcher;
import com.economicbriefing.collector.parser.ArticleNormalizer;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import com.economicbriefing.util.KstDateTimeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Fetches a user-supplied Yonhap article into the same Article model used by RSS collection. */
@Component
public class ManualArticleFetcher {

    private static final Pattern META = Pattern.compile("<meta\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "([\\w:-]+)\\s*=\\s*([\"'])(.*?)\\2", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private final HttpClient httpClient;
    private final AppProperties appProperties;
    private final ArticleNormalizer normalizer;

    @Autowired
    public ManualArticleFetcher(AppProperties appProperties, ArticleNormalizer normalizer) {
        this(HttpClient.newBuilder().connectTimeout(appProperties.timeouts().rssHttp())
                .followRedirects(HttpClient.Redirect.NORMAL).build(), appProperties, normalizer);
    }

    ManualArticleFetcher(HttpClient httpClient, AppProperties appProperties, ArticleNormalizer normalizer) {
        this.httpClient = httpClient;
        this.appProperties = appProperties;
        this.normalizer = normalizer;
    }

    public Article fetch(String rawUrl) {
        URI uri = validateYonhapUrl(rawUrl);
        String html = fetchHtml(uri);
        String title = meta(html, "og:title");
        if (title.isBlank()) title = titleTag(html);
        if (title.isBlank()) throw new IllegalArgumentException("기사 제목을 찾을 수 없습니다.");

        String summary = meta(html, "og:description");
        ArticleBodyFetcher.ExtractedBody extracted = ArticleBodyFetcher.extractYonhap(html);
        String content = extracted.body();
        if (content.isBlank()) throw new IllegalArgumentException("기사 본문을 찾을 수 없습니다.");

        return normalizer.normalize(title, uri.toString(), OffsetDateTime.now(KstDateTimeUtil.KST), summary,
                "연합뉴스", ArticleSourceType.NEWS_MEDIA, List.of(NewsCategory.OTHER), content, uri.toString());
    }

    private URI validateYonhapUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) throw new IllegalArgumentException("기사 URL이 필요합니다.");
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 기사 URL입니다.");
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || !(host.equals("yna.co.kr") || host.equals("www.yna.co.kr"))) {
            throw new IllegalArgumentException("현재는 연합뉴스 HTTPS 기사 URL만 지원합니다.");
        }
        return uri;
    }

    private String fetchHtml(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(appProperties.timeouts().rssHttp())
                    .header("User-Agent", "EconomicBriefing/1.0").GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) throw new IllegalArgumentException("원문 요청이 실패했습니다: HTTP " + response.statusCode());
            return response.body();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("원문을 가져오지 못했습니다.");
        }
    }

    private static String meta(String html, String expectedProperty) {
        Matcher meta = META.matcher(html);
        while (meta.find()) {
            Matcher attribute = ATTRIBUTE.matcher(meta.group(1));
            String name = null;
            String content = null;
            while (attribute.find()) {
                if (attribute.group(1).equalsIgnoreCase("property") || attribute.group(1).equalsIgnoreCase("name")) name = attribute.group(3);
                if (attribute.group(1).equalsIgnoreCase("content")) content = attribute.group(3);
            }
            if (expectedProperty.equalsIgnoreCase(name) && content != null) return clean(content);
        }
        return "";
    }

    private static String titleTag(String html) {
        Matcher title = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        return title.find() ? clean(title.group(1)) : "";
    }

    private static String clean(String value) {
        return TAG.matcher(value).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }
}
