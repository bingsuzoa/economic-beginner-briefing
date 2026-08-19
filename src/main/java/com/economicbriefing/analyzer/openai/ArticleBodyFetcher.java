package com.economicbriefing.analyzer.openai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.economicbriefing.config.AppProperties;
import com.economicbriefing.domain.article.Article;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ArticleBodyFetcher {

    private static final Logger log = LoggerFactory.getLogger(ArticleBodyFetcher.class);
    private static final Pattern YONHAP_BODY = Pattern.compile(
            "class=\"story-news article\"(.*?)<p class=\"txt-copyright", Pattern.DOTALL);
    private static final Pattern PARAGRAPH = Pattern.compile("<p(?:\\s[^>]*)?>(.*?)</p>", Pattern.DOTALL);

    private final HttpClient httpClient;
    private final AppProperties appProperties;

    @Autowired
    public ArticleBodyFetcher(AppProperties appProperties) {
        this(HttpClient.newBuilder()
                .connectTimeout(appProperties.timeouts().rssHttp())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), appProperties);
    }

    ArticleBodyFetcher(HttpClient httpClient, AppProperties appProperties) {
        this.httpClient = httpClient;
        this.appProperties = appProperties;
    }

    public List<Article> enrich(List<Article> articles) {
        return articles.stream().map(this::enrich).toList();
    }

    Article enrich(Article article) {
        if (!supports(article)) return article;
        try {
            String html = fetchHtml(URI.create(article.url()));
            ExtractedBody extracted = extractYonhap(html);
            if (!goodEnough(extracted)) {
                log.info("Article body fetch fallback: articleId={}, source={}, url={}, paragraphs={}, bodyChars={}, reason=low_quality",
                        article.id(), article.sourceName(), article.url(), extracted.paragraphs(), extracted.body().length());
                return article;
            }
            log.info("Article body fetch success: articleId={}, source={}, url={}, paragraphs={}, bodyChars={}",
                    article.id(), article.sourceName(), article.url(), extracted.paragraphs(), extracted.body().length());
            return withContent(article, extracted.body());
        } catch (Exception e) {
            log.info("Article body fetch fallback: articleId={}, source={}, url={}, reason={}",
                    article.id(), article.sourceName(), article.url(), e.getClass().getSimpleName());
            return article;
        }
    }

    protected String fetchHtml(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(appProperties.timeouts().rssHttp())
                .header("User-Agent", "EconomicBriefing/1.0")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    static ExtractedBody extractYonhap(String html) {
        var body = YONHAP_BODY.matcher(html);
        if (!body.find()) return new ExtractedBody("", 0);

        List<String> paragraphs = new ArrayList<>();
        var paragraph = PARAGRAPH.matcher(body.group(1));
        while (paragraph.find()) {
            String text = clean(paragraph.group(1));
            if (!text.isBlank() && !boilerplate(text)) paragraphs.add(text);
        }
        return new ExtractedBody(String.join("\n", paragraphs).trim(), paragraphs.size());
    }

    private static boolean goodEnough(ExtractedBody extracted) {
        if (extracted.paragraphs() < 2) return false;
        String body = extracted.body();
        return body.length() >= 250 && body.chars().filter(ch -> ch == '.'
                || ch == '?' || ch == '!' || ch == '다').count() >= 3;
    }

    private static boolean supports(Article article) {
        if (article == null || article.url() == null || article.url().isBlank()) return false;
        try {
            String host = URI.create(article.url()).getHost();
            return host != null && (host.equals("www.yna.co.kr") || host.equals("yna.co.kr"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String clean(String html) {
        return html.replaceAll("<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replace("&apos;", "'")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s*", "\n")
                .trim();
    }

    private static boolean boilerplate(String text) {
        return text.contains("RSS 피드")
                || text.contains("무단 전재")
                || text.contains("AI 학습")
                || text.contains("뉴스 앱")
                || text.matches(".*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*");
    }

    private static Article withContent(Article article, String content) {
        return new Article(
                article.id(), article.title(), article.summary(), article.sourceName(), article.sourceType(),
                article.publishedAt(), article.collectedAt(), article.url(), article.categories(),
                article.language(), content);
    }

    record ExtractedBody(String body, int paragraphs) {}
}
