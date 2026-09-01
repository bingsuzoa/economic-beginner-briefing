package com.economicbriefing.economicflow;

import java.time.OffsetDateTime;

import com.economicbriefing.domain.article.Article;

public record ArticleContext(
        String articleId, String title, String url, OffsetDateTime publishedAt, String body) {
    public static ArticleContext from(Article article) {
        return new ArticleContext(article.id(), article.title(), article.url(), article.publishedAt(), article.content());
    }
}
