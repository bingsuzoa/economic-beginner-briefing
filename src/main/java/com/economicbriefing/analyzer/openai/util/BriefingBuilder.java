package com.economicbriefing.analyzer.openai.util;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
            List<Article> articles,
            String modelName,
            String promptVersion,
            String briefingTitle,
            Integer targetHour) {

        Map<String, Article> articleMap = articles.stream()
                .collect(Collectors.toMap(Article::id, Function.identity()));

        List<AnalyzedNews> news = aiResponse.news().stream()
                .map(aiNews -> mapToAnalyzedNews(aiNews, articleMap))
                .toList();

        List<EconomicTerm> glossary = aiResponse.glossary() != null
                ? aiResponse.glossary().stream()
                        .map(BriefingBuilder::mapToEconomicTerm)
                        .toList()
                : Collections.emptyList();

        BriefingMetadata metadata = new BriefingMetadata(
                articles.size(),
                articles.size(),
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

        return new Briefing(
                briefingId,
                targetDate,
                KstDateTimeUtil.now(),
                title,
                aiResponse.overallSummary(),
                news,
                glossary,
                metadata
        );
    }

    private static AnalyzedNews mapToAnalyzedNews(
            AiResponse.AiAnalyzedNews aiNews,
            Map<String, Article> articleMap) {

        List<SourceReference> sources = aiNews.sources().stream()
                .map(src -> {
                    Article article = articleMap.get(src.articleId());
                    return new SourceReference(
                            src.articleId(),
                            article != null ? article.sourceName() : "Unknown",
                            article != null ? article.title() : "Unknown",
                            article != null ? article.url() : "https://unknown",
                            article != null ? article.publishedAt()
                                    : OffsetDateTime.parse("1970-01-01T00:00:00+09:00"),
                            src.isPrimary()
                    );
                })
                .toList();

        List<EconomicTerm> economicTerms = aiNews.economicTerms() != null
                ? aiNews.economicTerms().stream()
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
                aiNews.representativeTitle(),
                category,
                aiNews.importance(),
                aiNews.whyImportant(),
                null,
                null,
                aiNews.oneLineSummary(),
                aiNews.explanation(),
                evidenceStatus,
                aiNews.uncertaintyNote(),
                economicTerms,
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
