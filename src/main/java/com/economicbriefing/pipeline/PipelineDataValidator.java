package com.economicbriefing.pipeline;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.economicbriefing.analyzer.dto.AnalyzeNewsResult;
import com.economicbriefing.collector.dto.CollectNewsResult;
import com.economicbriefing.domain.analysis.AnalyzedNews;
import com.economicbriefing.domain.article.Article;
import org.springframework.stereotype.Component;

@Component
public class PipelineDataValidator {

    public record ValidationWarning(String stage, String message) {}

    public record CollectValidationResult(
        List<Article> validArticles,
        List<ValidationWarning> warnings
    ) {}

    public record AnalyzeValidationResult(
        boolean valid,
        List<ValidationWarning> warnings
    ) {}

    public CollectValidationResult validateCollectResult(CollectNewsResult result, LocalDate targetDate) {
        List<ValidationWarning> warnings = new ArrayList<>();

        if (!result.targetDate().equals(targetDate)) {
            warnings.add(new ValidationWarning("collect",
                    "targetDate mismatch: expected " + targetDate + ", got " + result.targetDate()));
        }

        List<Article> validArticles = new ArrayList<>();
        for (Article article : result.articles()) {
            if (article.id() == null || article.id().isBlank()
                    || article.title() == null || article.title().isBlank()
                    || article.url() == null || article.url().isBlank()) {
                warnings.add(new ValidationWarning("collect",
                        "Article missing required field: id=" + article.id()
                                + ", title=" + (article.title() != null ? "present" : "missing")
                                + ", url=" + (article.url() != null ? "present" : "missing")));
            } else {
                validArticles.add(article);
            }
        }

        return new CollectValidationResult(validArticles, warnings);
    }

    public AnalyzeValidationResult validateAnalyzeResult(AnalyzeNewsResult result, LocalDate targetDate) {
        List<ValidationWarning> warnings = new ArrayList<>();

        if (!result.briefing().targetDate().equals(targetDate)) {
            warnings.add(new ValidationWarning("analyze",
                    "targetDate mismatch: expected " + targetDate
                            + ", got " + result.briefing().targetDate()));
        }

        if (result.briefing().news().isEmpty()) {
            return new AnalyzeValidationResult(false, warnings);
        }

        List<AnalyzedNews> validNews = new ArrayList<>();
        for (AnalyzedNews news : result.briefing().news()) {
            if (news.sources() == null || news.sources().isEmpty()) {
                warnings.add(new ValidationWarning("analyze",
                        "News \"" + news.representativeTitle() + "\" has no source references, excluded"));
                continue;
            }
            boolean hasUrl = news.sources().stream().anyMatch(s -> s.url() != null && !s.url().isBlank());
            if (!hasUrl) {
                warnings.add(new ValidationWarning("analyze",
                        "News \"" + news.representativeTitle() + "\" has no source URLs, excluded"));
                continue;
            }
            validNews.add(news);
        }

        if (validNews.isEmpty()) {
            return new AnalyzeValidationResult(false, warnings);
        }

        return new AnalyzeValidationResult(true, warnings);
    }
}
