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

        Briefing briefing = BriefingBuilder.build(
                aiResponse,
                request.targetDate(),
                request.articles(),
                openAiProperties.model(),
                "v1",
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
        AiResponse response;
        try {
            response = objectMapper.readValue(content, AiResponse.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse AI response as JSON", e);
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR, e);
        }

        if (response.overallSummary() == null || response.overallSummary().isEmpty()) {
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR);
        }
        if (response.news() == null || response.news().isEmpty()) {
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR);
        }

        for (AiResponse.AiAnalyzedNews news : response.news()) {
            if (news.id() == null || news.id().isBlank()) {
                throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR);
            }
            if (news.representativeTitle() == null || news.representativeTitle().isBlank()) {
                throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR);
            }
            if (news.sources() == null || news.sources().isEmpty()) {
                throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR);
            }
        }

        return response;
    }
}
