package com.economicbriefing.analyzer.openai;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.economicbriefing.analyzer.NewsAnalyzer;
import com.economicbriefing.analyzer.dto.AnalyzeNewsRequest;
import com.economicbriefing.analyzer.dto.AnalyzeNewsResult;
import com.economicbriefing.analyzer.dto.ArticleValidationResult;
import com.economicbriefing.analyzer.openai.dto.AiResponse;
import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.analyzer.openai.prompt.AnalysisPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.ArticleAnalyzerPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.ArticleValidatorPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.SystemPromptBuilder;
import com.economicbriefing.analyzer.openai.util.BriefingBuilder;
import com.economicbriefing.analyzer.openai.util.RetryExecutor;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.config.OpenAiProperties;
import com.economicbriefing.domain.briefing.Briefing;
import com.economicbriefing.exception.AnalyzeException;
import com.economicbriefing.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "briefing.dry-run", havingValue = "false")
public class OpenAiNewsAnalyzer implements NewsAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(OpenAiNewsAnalyzer.class);

    private final OpenAiClient aiClient;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties openAiProperties;
    private final AppProperties appProperties;
    private final ArticleBodyFetcher articleBodyFetcher;

    public OpenAiNewsAnalyzer(
            OpenAiClient aiClient,
            ObjectMapper objectMapper,
            OpenAiProperties openAiProperties,
            AppProperties appProperties,
            ArticleBodyFetcher articleBodyFetcher) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.openAiProperties = openAiProperties;
        this.appProperties = appProperties;
        this.articleBodyFetcher = articleBodyFetcher;
    }

    @Override
    public AnalyzeNewsResult analyze(AnalyzeNewsRequest request) {
        if (request.articles().isEmpty()) {
            throw new AnalyzeException(ErrorCode.ANALYZE_EMPTY_INPUT);
        }

        log.info("Starting AI analysis (3-stage): articles={}, targetDate={}, maxNews={}",
                request.articles().size(), request.targetDate(), request.maxSelectedNews());

        // Stage 1: Selection
        log.info("Stage 1: Selecting important articles...");
        String selectionPrompt = com.economicbriefing.analyzer.openai.prompt.SelectionPromptBuilder.build(
                request.articles(),
                request.targetDate(),
                request.maxSelectedNews(),
                request.audience()
        );

        com.economicbriefing.analyzer.openai.dto.SelectionResponse selectionResponse = RetryExecutor.execute(
                () -> callAndParseSelection(selectionPrompt),
                appProperties.retry()
        );

        List<String> selectedIds = selectionResponse.selectedArticleIds();
        log.info("Stage 1 completed: selected {} articles", selectedIds.size());

        // Filter selected articles in order
        Map<String, com.economicbriefing.domain.article.Article> articleMap = request.articles().stream()
                .collect(Collectors.toMap(
                        com.economicbriefing.domain.article.Article::id,
                        a -> a
                ));

        List<com.economicbriefing.domain.article.Article> selectedArticles = articleBodyFetcher.enrich(selectedIds.stream()
                .map(articleMap::get)
                .filter(a -> a != null)
                .toList());

        // Stage 2: Article analysis
        log.info("Stage 2: Structuring selected article evidence...");
        String articleAnalyzerPrompt = ArticleAnalyzerPromptBuilder.build(selectedArticles);
        ArticleAnalysisResponse articleAnalysis = RetryExecutor.execute(
                () -> callAndParseArticleAnalysis(articleAnalyzerPrompt, selectedArticles),
                appProperties.retry()
        );
        String articleAnalysisJson = toJson(articleAnalysis);
        String validationPrompt = ArticleValidatorPromptBuilder.build(selectedArticles, articleAnalysisJson);
        ArticleValidationResult itemValidation = RetryExecutor.execute(
                () -> callAndParseValidation(
                        ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT,
                        validationPrompt, selectedArticles, articleAnalysis,
                        Set.of(ArticleValidationResult.FindingType.WRONG_TYPE,
                                ArticleValidationResult.FindingType.WRONG_SPEAKER,
                                ArticleValidationResult.FindingType.UNSUPPORTED,
                                ArticleValidationResult.FindingType.INACCURATE)),
                appProperties.retry()
        );
        ArticleValidationResult missingReview = RetryExecutor.execute(
                () -> callAndParseValidation(
                        ArticleValidatorPromptBuilder.MISSING_REVIEW_SYSTEM_PROMPT,
                        validationPrompt, selectedArticles, articleAnalysis,
                        Set.of(ArticleValidationResult.FindingType.MISSING)),
                appProperties.retry()
        );
        ArticleValidationResult validation = ArticleValidationMerger.merge(
                selectedArticles, articleAnalysis, itemValidation, missingReview);
        log.info("Stage 2 completed: structured {} articles, validator findings={}",
                articleAnalysis.articles().size(),
                validation.articles().stream().mapToInt(a -> a.findings().size()).sum());

        // Stage 3: Final analysis
        log.info("Stage 3: Analyzing selected articles...");
        String analysisPrompt = AnalysisPromptBuilder.build(
                selectedArticles,
                request.targetDate(),
                request.maxSelectedNews(),
                request.audience(),
                articleAnalysisJson
        );

        AiResponse aiResponse = RetryExecutor.execute(
                () -> callAndParseAnalysis(analysisPrompt),
                appProperties.retry()
        );

        log.info("Stage 3 completed: analyzed {} news items", aiResponse.news().size());

        Briefing briefing = BriefingBuilder.build(
                aiResponse,
                request.targetDate(),
                selectedArticles,
                request.articles().size(),
                openAiProperties.model(),
                "v4-article-analyzer",
                request.briefingTitle(),
                request.targetHour()
        );

        List<String> rejectedArticleIds = request.articles().stream()
                .map(com.economicbriefing.domain.article.Article::id)
                .filter(id -> !selectedIds.contains(id))
                .toList();

        log.info("AI analysis completed: selected={}, rejected={}",
                briefing.news().size(), rejectedArticleIds.size());

        return new AnalyzeNewsResult(briefing, rejectedArticleIds, List.of(), validation);
    }

    private com.economicbriefing.analyzer.openai.dto.SelectionResponse callAndParseSelection(String userPrompt) {
        String content = aiClient.complete(
                com.economicbriefing.analyzer.openai.prompt.SelectionPromptBuilder.SYSTEM_PROMPT,
                userPrompt
        );
        return parseSelection(content);
    }

    private AiResponse callAndParseAnalysis(String userPrompt) {
        String content = aiClient.complete(SystemPromptBuilder.SYSTEM_PROMPT, userPrompt);
        return parseAndValidateAnalysis(content);
    }

    private ArticleAnalysisResponse callAndParseArticleAnalysis(
            String userPrompt,
            List<com.economicbriefing.domain.article.Article> selectedArticles) {
        String content = aiClient.complete(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT, userPrompt, 0);
        try {
            ArticleAnalysisResponse response = objectMapper.readValue(content, ArticleAnalysisResponse.class);
            validateArticleAnalysis(response, selectedArticles);
            return response;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("Failed to parse or validate Article Analyzer response", e);
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR, e);
        }
    }

    private void validateArticleAnalysis(
            ArticleAnalysisResponse response,
            List<com.economicbriefing.domain.article.Article> selectedArticles) {
        if (response.articles() == null || response.articles().size() != selectedArticles.size()) {
            throw new IllegalArgumentException("Article Analyzer result count does not match selection");
        }
        for (int i = 0; i < selectedArticles.size(); i++) {
            ArticleAnalysisResponse.ArticleAnalysis analysis = response.articles().get(i);
            if (analysis == null || !selectedArticles.get(i).id().equals(analysis.articleId())
                    || analysis.issues() == null || analysis.issues().isEmpty()
                    || analysis.issues().stream().anyMatch(issue -> issue == null
                            || issue.name() == null || issue.name().isBlank()
                            || issue.mainFacts() == null || issue.changes() == null
                            || issue.relations() == null || issue.statements() == null
                            || issue.keyTerms() == null)) {
                throw new IllegalArgumentException("Invalid Article Analyzer result at index " + i);
            }
        }
    }

    private String toJson(ArticleAnalysisResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR, e);
        }
    }

    private ArticleValidationResult callAndParseValidation(
            String systemPrompt,
            String userPrompt,
            List<com.economicbriefing.domain.article.Article> selectedArticles,
            ArticleAnalysisResponse baseline,
            Set<ArticleValidationResult.FindingType> allowedTypes) {
        String content = aiClient.complete(systemPrompt, userPrompt, 0);
        try {
            return ArticleValidationIntegrity.parseAndValidate(
                    objectMapper, content, selectedArticles, baseline, allowedTypes);
        } catch (Exception e) {
            log.error("Failed to parse or validate Article Validator response", e);
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR, e);
        }
    }

    private com.economicbriefing.analyzer.openai.dto.SelectionResponse parseSelection(String content) {
        log.info("Parsing selection response...");
        try {
            com.economicbriefing.analyzer.openai.dto.SelectionResponse response =
                    objectMapper.readValue(content, com.economicbriefing.analyzer.openai.dto.SelectionResponse.class);
            log.info("Selection parsed successfully: {} articles selected", response.selectedArticleIds().size());
            return response;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse selection response", e);
            log.error("Full content for debugging: {}", content);
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR, e);
        }
    }

    private AiResponse parseAndValidateAnalysis(String content) {
        log.info("=== PARSING ANALYSIS JSON START ===");
        log.info("Content length: {} characters", content.length());
        log.info("First 500 chars: {}", content.substring(0, Math.min(500, content.length())));
        log.info("=== PARSING ANALYSIS JSON END ===");

        AiResponse response;
        try {
            response = objectMapper.readValue(content, AiResponse.class);
            log.info("=== PARSED SUCCESSFULLY ===");
            log.info("Parsed news count: {}", response.news() != null ? response.news().size() : 0);
            if (response.news() != null && !response.news().isEmpty()) {
                var firstNews = response.news().get(0);
                log.info("First news terms: {}", firstNews.terms());
            }
        } catch (JsonProcessingException e) {
            log.error("=== PARSE FAILED ===");
            log.error("Failed to parse AI response as JSON", e);
            log.error("Full content for debugging:");
            log.error(content);
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR, e);
        }

        // overallSummary is optional - null or empty is valid
        if (response.news() == null || response.news().isEmpty()) {
            log.error("Validation failed: news is null or empty");
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR);
        }

        for (int i = 0; i < response.news().size(); i++) {
            AiResponse.AiAnalyzedNews news = response.news().get(i);
            String newsPrefix = "News[" + i + "] ";

            if (news.id() == null || news.id().isBlank()) {
                log.error("{}id is null or blank", newsPrefix);
                throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR);
            }
            if (news.easyTitle() == null || news.easyTitle().isBlank()) {
                log.error("{}easyTitle is null or blank", newsPrefix);
                throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR);
            }
            if (news.threeLineSummary() == null || news.threeLineSummary().isEmpty()) {
                log.error("{}threeLineSummary is null or empty", newsPrefix);
                throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR);
            }
            if (news.whatHappened() == null || news.whatHappened().isBlank()) {
                log.error("{}whatHappened is null or blank", newsPrefix);
                throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR);
            }
        }

        return response;
    }

}
