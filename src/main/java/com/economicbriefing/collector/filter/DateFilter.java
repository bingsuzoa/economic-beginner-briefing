package com.economicbriefing.collector.filter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.economicbriefing.domain.article.Article;
import org.springframework.stereotype.Component;

@Component
public class DateFilter {

    public DateFilterResult filter(List<Article> articles, OffsetDateTime startTime, OffsetDateTime endTime) {
        List<Article> accepted = new ArrayList<>();
        List<Article> rejected = new ArrayList<>();

        for (Article article : articles) {
            if (article.publishedAt() == null) {
                rejected.add(article);
                continue;
            }

            OffsetDateTime published = article.publishedAt();
            if (!published.isBefore(startTime) && !published.isAfter(endTime)) {
                accepted.add(article);
            } else {
                rejected.add(article);
            }
        }

        return new DateFilterResult(accepted, rejected);
    }

    public record DateFilterResult(List<Article> accepted, List<Article> rejected) {}
}
