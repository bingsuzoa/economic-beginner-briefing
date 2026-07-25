package com.economicbriefing.classifier;

import com.economicbriefing.analyzer.openai.OpenAiClient;
import com.economicbriefing.analyzer.openai.util.RetryExecutor;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.exception.AnalyzeException;
import com.economicbriefing.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "briefing.dry-run", havingValue = "false")
public class OpenAiTeacherClassifier implements TeacherClassifier {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTeacherClassifier.class);

    private final OpenAiClient aiClient;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public OpenAiTeacherClassifier(OpenAiClient aiClient, ObjectMapper objectMapper,
                                   AppProperties appProperties) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    @Override
    public TeacherLabelResponse classify(Article article) {
        log.info("Classifying article: id={}, title={}", article.id(),
                article.title().substring(0, Math.min(50, article.title().length())));

        return RetryExecutor.execute(
                () -> callAndParse(article),
                appProperties.retry()
        );
    }

    private TeacherLabelResponse callAndParse(Article article) {
        String userPrompt = TeacherPromptBuilder.buildUserPrompt(article);
        String content = aiClient.complete(TeacherPromptBuilder.SYSTEM_PROMPT, userPrompt,
                appProperties.teacher().model());

        try {
            return objectMapper.readValue(content, TeacherLabelResponse.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse teacher label response: {}", content, e);
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR, e);
        }
    }
}
