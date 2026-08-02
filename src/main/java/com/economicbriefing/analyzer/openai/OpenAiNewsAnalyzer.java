package com.economicbriefing.analyzer.openai;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.economicbriefing.analyzer.NewsAnalyzer;
import com.economicbriefing.analyzer.dto.AnalyzeNewsRequest;
import com.economicbriefing.analyzer.dto.AnalyzeNewsResult;
import com.economicbriefing.analyzer.openai.dto.AiResponse;
import com.economicbriefing.analyzer.openai.prompt.AnalysisPromptBuilder;
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

    public OpenAiNewsAnalyzer(
            OpenAiClient aiClient,
            ObjectMapper objectMapper,
            OpenAiProperties openAiProperties,
            AppProperties appProperties) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.openAiProperties = openAiProperties;
        this.appProperties = appProperties;
    }

    @Override
    public AnalyzeNewsResult analyze(AnalyzeNewsRequest request) {
        if (request.articles().isEmpty()) {
            throw new AnalyzeException(ErrorCode.ANALYZE_EMPTY_INPUT);
        }

        log.info("Starting AI analysis: articles={}, targetDate={}, maxNews={}",
                request.articles().size(), request.targetDate(), request.maxSelectedNews());

        String userPrompt = AnalysisPromptBuilder.build(
                request.articles(),
                request.targetDate(),
                request.maxSelectedNews(),
                request.audience()
        );

        AiResponse aiResponse = RetryExecutor.execute(
                () -> callAndParse(userPrompt),
                appProperties.retry()
        );

        // Validate article IDs from AI response
        validateArticleIds(aiResponse, request.articles());

        Briefing briefing = BriefingBuilder.build(
                aiResponse,
                request.targetDate(),
                request.articles(),
                openAiProperties.model(),
                "v2",
                request.briefingTitle(),
                request.targetHour()
        );

        Set<String> selectedIds = aiResponse.news().stream()
                .flatMap(n -> n.sources().stream())
                .map(AiResponse.AiSourceReference::articleId)
                .collect(Collectors.toSet());

        List<String> rejectedArticleIds = request.articles().stream()
                .map(a -> a.id())
                .filter(id -> !selectedIds.contains(id))
                .toList();

        log.info("AI analysis completed: selected={}, rejected={}",
                briefing.news().size(), rejectedArticleIds.size());

        return new AnalyzeNewsResult(briefing, rejectedArticleIds, List.of());
    }

    private AiResponse callAndParse(String userPrompt) {
        String content = aiClient.complete(SystemPromptBuilder.SYSTEM_PROMPT, userPrompt);
        return parseAndValidate(content);
    }

    private AiResponse parseAndValidate(String content) {
        log.info("=== PARSING JSON START ===");
        log.info("Content length: {} characters", content.length());
        log.info("First 500 chars: {}", content.substring(0, Math.min(500, content.length())));
        log.info("=== PARSING JSON END ===");

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
            if (news.sources() == null || news.sources().isEmpty()) {
                log.error("{}sources is null or empty", newsPrefix);
                throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR);
            }
        }

        return response;
    }

    private void validateArticleIds(AiResponse response, List<com.economicbriefing.domain.article.Article> inputArticles) {
        Set<String> validArticleIds = inputArticles.stream()
                .map(a -> a.id())
                .collect(Collectors.toSet());

        for (int i = 0; i < response.news().size(); i++) {
            AiResponse.AiAnalyzedNews news = response.news().get(i);
            for (AiResponse.AiSourceReference source : news.sources()) {
                if (!validArticleIds.contains(source.articleId())) {
                    log.error("AI returned invalid article ID: newsIndex={}, invalidId={}, validIds={}",
                            i, source.articleId(), validArticleIds);
                    log.error("News easyTitle: {}", news.easyTitle());
                    log.error("This indicates AI hallucinated or mixed up article IDs");
                    throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR,
                            new IllegalStateException("AI returned article ID that doesn't exist in input: " + source.articleId()));
                }
            }
        }
        log.info("Article ID validation passed: all {} article IDs are valid",
                response.news().stream().flatMap(n -> n.sources().stream()).count());
    }
}
