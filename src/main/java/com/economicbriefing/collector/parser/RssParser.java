package com.economicbriefing.collector.parser;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import com.economicbriefing.config.AppProperties;
import com.economicbriefing.exception.CollectException;
import com.economicbriefing.exception.ErrorCode;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RssParser {

    private static final Logger log = LoggerFactory.getLogger(RssParser.class);

    private final HttpClient httpClient;
    private final AppProperties appProperties;

    public RssParser(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(appProperties.timeouts().rssHttp())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<RssItem> parse(String feedUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(feedUrl))
                    .timeout(appProperties.timeouts().rssHttp())
                    .header("User-Agent", "EconomicBriefing/1.0")
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() >= 400) {
                throw new CollectException(ErrorCode.COLLECT_SOURCE_UNAVAILABLE, feedUrl);
            }

            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(response.body()));

            List<RssItem> items = new ArrayList<>();
            for (SyndEntry entry : feed.getEntries()) {
                items.add(RssItem.fromEntry(entry));
            }
            return items;

        } catch (CollectException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new CollectException(ErrorCode.COLLECT_SOURCE_TIMEOUT, feedUrl, e);
        } catch (Exception e) {
            throw new CollectException(ErrorCode.COLLECT_PARSE_ERROR, feedUrl, e);
        }
    }
}
