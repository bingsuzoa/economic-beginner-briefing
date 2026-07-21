package com.economicbriefing.collector.filter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.economicbriefing.domain.article.Article;
import org.springframework.stereotype.Component;

@Component
public class QualityValidator {

    private static final int MIN_TITLE_LENGTH = 5;

    private static final List<String> AD_KEYWORDS = List.of(
            "[AD]", "[광고]", "[제휴]", "[이벤트]", "[홍보]",
            "광고", "제휴", "이벤트 안내", "협찬"
    );

    public QualityValidationResult validate(List<Article> articles) {
        List<Article> valid = new ArrayList<>();
        List<Article> invalid = new ArrayList<>();

        for (Article article : articles) {
            if (isValid(article)) {
                valid.add(article);
            } else {
                invalid.add(article);
            }
        }

        return new QualityValidationResult(valid, invalid);
    }

    private boolean isValid(Article article) {
        if (article.title() == null || article.title().trim().length() < MIN_TITLE_LENGTH) {
            return false;
        }

        if (article.url() == null || !isValidUrl(article.url())) {
            return false;
        }

        if (article.publishedAt() == null) {
            return false;
        }

        return !containsAdKeyword(article.title());
    }

    private boolean isValidUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return "http".equals(scheme) || "https".equals(scheme);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean containsAdKeyword(String title) {
        for (String keyword : AD_KEYWORDS) {
            if (title.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public record QualityValidationResult(List<Article> valid, List<Article> invalid) {}
}
