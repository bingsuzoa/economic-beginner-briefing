package com.economicbriefing.analyzer.openai.util;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import com.economicbriefing.analyzer.openai.dto.AiResponse;
import com.economicbriefing.domain.analysis.AnalyzedNews;
import com.economicbriefing.domain.analysis.EconomicTerm;
import com.economicbriefing.domain.analysis.NewsEvidenceStatus;
import com.economicbriefing.domain.analysis.SourceReference;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.NewsCategory;
import com.economicbriefing.domain.briefing.Briefing;
import com.economicbriefing.domain.briefing.BriefingMetadata;
import com.economicbriefing.util.IdGenerator;
import com.economicbriefing.util.KstDateTimeUtil;

public final class BriefingBuilder {

    private BriefingBuilder() {}

    public static Briefing build(
            AiResponse aiResponse,
            LocalDate targetDate,
            List<Article> selectedArticles,
            int totalArticleCount,
            String modelName,
            String promptVersion,
            String briefingTitle,
            Integer targetHour) {

        // 순서 기반 매핑: aiResponse.news()[i] = selectedArticles[i]
        List<AnalyzedNews> news = IntStream.range(0, aiResponse.news().size())
                .mapToObj(i -> {
                    AiResponse.AiAnalyzedNews aiNews = aiResponse.news().get(i);
                    Article article = selectedArticles.get(i);
                    return mapToAnalyzedNews(aiNews, article);
                })
                .toList();

        List<EconomicTerm> glossary = aiResponse.glossary() != null
                ? aiResponse.glossary().stream()
                        .map(BriefingBuilder::mapToEconomicTerm)
                        .toList()
                : Collections.emptyList();

        BriefingMetadata metadata = new BriefingMetadata(
                totalArticleCount,
                totalArticleCount,
                news.size(),
                modelName,
                promptVersion
        );

        String title = briefingTitle != null
                ? briefingTitle
                : targetDate + " 경제 브리핑";

        String briefingId = targetHour != null
                ? IdGenerator.briefingId(targetDate, targetHour)
                : IdGenerator.briefingId(targetDate);

        List<String> overallSummary = aiResponse.overallSummary() != null
                ? aiResponse.overallSummary()
                : Collections.emptyList();

        return new Briefing(
                briefingId,
                targetDate,
                KstDateTimeUtil.now(),
                title,
                overallSummary,
                news,
                glossary,
                metadata
        );
    }

    private static AnalyzedNews mapToAnalyzedNews(
            AiResponse.AiAnalyzedNews aiNews,
            Article article) {

        SourceReference source = new SourceReference(
                article.id(),
                article.sourceName(),
                article.title(),
                article.url(),
                article.publishedAt(),
                true
        );
        List<SourceReference> sources = List.of(source);

        List<EconomicTerm> terms = aiNews.terms() != null
                ? aiNews.terms().stream()
                        .map(BriefingBuilder::mapToEconomicTerm)
                        .toList()
                : Collections.emptyList();

        NewsCategory category;
        try {
            category = NewsCategory.fromValue(aiNews.category());
        } catch (IllegalArgumentException e) {
            category = NewsCategory.OTHER;
        }

        NewsEvidenceStatus evidenceStatus;
        try {
            evidenceStatus = NewsEvidenceStatus.fromValue(aiNews.evidenceStatus());
        } catch (IllegalArgumentException e) {
            evidenceStatus = NewsEvidenceStatus.EXPECTED;
        }

        return new AnalyzedNews(
                aiNews.id(),
                aiNews.easyTitle(),
                category,
                aiNews.importance(),
                aiNews.threeLineSummary() != null ? aiNews.threeLineSummary() : Collections.emptyList(),
                aiNews.whatHappened(),
                aiNews.whyItHappened(),
                aiNews.beginnerExplanation(),
                aiNews.economicImpact(),
                terms,
                evidenceStatus,
                sources
        );
    }

    private static EconomicTerm mapToEconomicTerm(AiResponse.AiEconomicTerm aiTerm) {
        return new EconomicTerm(
                aiTerm.term(),
                aiTerm.explanation(),
                aiTerm.example()
        );
    }
}
