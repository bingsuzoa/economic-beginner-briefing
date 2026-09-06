package com.economicbriefing.analyzer.dto;

import java.time.LocalDate;
import java.util.List;

import com.economicbriefing.domain.analysis.AudienceProfile;
import com.economicbriefing.domain.article.Article;

public record AnalyzeNewsRequest(
    List<Article> articles,
    LocalDate targetDate,
    int maxSelectedNews,
    AudienceProfile audience,
    String briefingTitle,
    Integer targetHour,
    boolean forceAllArticles
) {
    public AnalyzeNewsRequest(List<Article> articles, LocalDate targetDate, int maxSelectedNews,
            AudienceProfile audience, String briefingTitle, Integer targetHour) {
        this(articles, targetDate, maxSelectedNews, audience, briefingTitle, targetHour, false);
    }

    public static AnalyzeNewsRequest of(
            List<Article> articles,
            LocalDate targetDate,
            int maxSelectedNews,
            AudienceProfile audience) {
        return new AnalyzeNewsRequest(articles, targetDate, maxSelectedNews, audience, null, null, false);
    }
}
