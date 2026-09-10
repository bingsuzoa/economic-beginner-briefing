package com.economicbriefing.article;

import com.economicbriefing.config.AppProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class YonhapBodyFetcher {
    private static final Pattern BODY = Pattern.compile("class=\"story-news article\"(.*?)<p class=\"txt-copyright", Pattern.DOTALL);
    private static final Pattern PARAGRAPH = Pattern.compile("<p(?:\\s[^>]*)?>(.*?)</p>", Pattern.DOTALL);
    private final HttpClient http;
    private final AppProperties properties;

    public YonhapBodyFetcher(AppProperties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder().connectTimeout(properties.timeouts().rssHttp())
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public String fetch(String url) {
        try {
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(properties.timeouts().rssHttp())
                    .header("User-Agent", "EconomicBriefing/1.0").GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) throw new IllegalStateException("HTTP " + response.statusCode());
            return extract(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("article fetch interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("article fetch failed", e);
        }
    }

    static String extract(String html) {
        var body = BODY.matcher(html == null ? "" : html);
        if (!body.find()) throw new IllegalArgumentException("Yonhap article body not found");
        List<String> paragraphs = new ArrayList<>();
        var paragraph = PARAGRAPH.matcher(body.group(1));
        while (paragraph.find()) {
            String text = clean(paragraph.group(1));
            if (!text.isBlank() && !boilerplate(text)) paragraphs.add(text);
        }
        String result = String.join("\n", paragraphs).strip();
        if (paragraphs.size() < 2 || result.length() < 250) throw new IllegalArgumentException("Yonhap article body too short");
        return result;
    }

    public void markSuccess(ArticleEntity article, String body) {
        article.setBody(body);
        article.setBodyStatus("FULL_TEXT");
        article.setBodyFetchedAt(OffsetDateTime.now());
    }

    private static String clean(String html) {
        return html.replaceAll("<br\\s*/?>", "\n").replaceAll("<[^>]+>", " ")
                .replace("&apos;", "'").replace("&quot;", "\"").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\n\\s*", "\n").strip();
    }

    private static boolean boilerplate(String text) {
        return text.contains("RSS 피드") || text.contains("무단 전재") || text.contains("AI 학습")
                || text.matches(".*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*");
    }
}
