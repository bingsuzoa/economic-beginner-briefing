package com.economicbriefing.pipeline;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.economicbriefing.analyzer.NewsAnalyzer;
import com.economicbriefing.analyzer.dto.AnalyzeNewsRequest;
import com.economicbriefing.analyzer.dto.AnalyzeNewsResult;
import com.economicbriefing.classifier.ArticlePersistenceService;
import com.economicbriefing.classifier.EmbeddingService;
import com.economicbriefing.classifier.TeacherClassifier;
import com.economicbriefing.classifier.TeacherLabelResponse;
import com.economicbriefing.classifier.entity.ArticleAnalysisEntity;
import com.economicbriefing.classifier.entity.TeacherLabelEntity;
import com.economicbriefing.classifier.repository.ArticleAnalysisRepository;
import com.economicbriefing.classifier.repository.TeacherLabelRepository;
import com.economicbriefing.collector.NewsCollector;
import com.economicbriefing.collector.dto.CollectNewsRequest;
import com.economicbriefing.collector.dto.CollectNewsResult;
import com.economicbriefing.collector.filter.DiversitySelector;
import com.economicbriefing.collector.filter.RelevanceScorer;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.config.OpenAiProperties;
import com.economicbriefing.domain.analysis.AnalyzedNews;
import com.economicbriefing.domain.analysis.AudienceProfile;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.NewsCategory;
import com.economicbriefing.domain.execution.ExecutionError;
import com.economicbriefing.domain.execution.ExecutionLog;
import com.economicbriefing.domain.execution.PublicationDecision;
import com.economicbriefing.domain.briefing.Briefing;
import com.economicbriefing.exception.BriefingException;
import com.economicbriefing.exception.ErrorCode;
import com.economicbriefing.publisher.BriefingPublisher;
import com.economicbriefing.publisher.dto.PublishBriefingRequest;
import com.economicbriefing.publisher.dto.PublishBriefingResult;
import com.economicbriefing.publisher.dto.PublishChannelResult;
import com.economicbriefing.util.IdGenerator;
import com.economicbriefing.util.KstDateTimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BriefingPipeline {

    private static final Logger log = LoggerFactory.getLogger(BriefingPipeline.class);

    private final NewsCollector collector;
    private final NewsAnalyzer analyzer;
    private final BriefingPublisher publisher;
    private final ExecutionTracker executionTracker;
    private final PipelineDataValidator validator;
    private final RelevanceScorer relevanceScorer;
    private final DiversitySelector diversitySelector;
    private final AppProperties appProperties;
    private final OpenAiProperties openAiProperties;
    private final TeacherClassifier teacherClassifier;
    private final ArticlePersistenceService articlePersistenceService;
    private final TeacherLabelRepository teacherLabelRepository;
    private final ArticleAnalysisRepository articleAnalysisRepository;
    private final EmbeddingService embeddingService; // null when embedding disabled
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public BriefingPipeline(
            NewsCollector collector,
            NewsAnalyzer analyzer,
            BriefingPublisher publisher,
            ExecutionTracker executionTracker,
            PipelineDataValidator validator,
            RelevanceScorer relevanceScorer,
            DiversitySelector diversitySelector,
            AppProperties appProperties,
            OpenAiProperties openAiProperties,
            TeacherClassifier teacherClassifier,
            ArticlePersistenceService articlePersistenceService,
            TeacherLabelRepository teacherLabelRepository,
            ArticleAnalysisRepository articleAnalysisRepository,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            @org.springframework.lang.Nullable EmbeddingService embeddingService) {
        this.collector = collector;
        this.analyzer = analyzer;
        this.publisher = publisher;
        this.executionTracker = executionTracker;
        this.validator = validator;
        this.relevanceScorer = relevanceScorer;
        this.diversitySelector = diversitySelector;
        this.appProperties = appProperties;
        this.openAiProperties = openAiProperties;
        this.teacherClassifier = teacherClassifier;
        this.articlePersistenceService = articlePersistenceService;
        this.teacherLabelRepository = teacherLabelRepository;
        this.articleAnalysisRepository = articleAnalysisRepository;
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
    }

    public ExecutionLog run(PipelineOptions options) {
        LocalDate targetDate = options.targetDate() != null
                ? options.targetDate()
                : KstDateTimeUtil.getCurrentDate();

        KstDateTimeUtil.TimeRange timeRange = options.timeRange();

        String executionId = IdGenerator.executionId();
        ExecutionLog executionLog = new ExecutionLog(executionId, targetDate, KstDateTimeUtil.now());

        log.info("Starting pipeline: executionId={}, targetDate={}", executionId, targetDate);

        // 0. Check duplicate execution
        String dedupeKey = timeRange != null
                ? targetDate + "T" + String.format("%02d", timeRange.hour())
                : targetDate.toString();

        PublicationDecision decision = executionTracker.checkDuplicate(dedupeKey);
        if (decision == PublicationDecision.SKIP_ALREADY_PUBLISHED) {
            log.info("Skipping already published: dedupeKey={}", dedupeKey);
            executionLog.markSuccess(KstDateTimeUtil.now());
            return executionLog;
        }

        // 1. Collect
        CollectNewsResult collectResult;
        try {
            CollectNewsRequest request = timeRange != null
                    ? CollectNewsRequest.of(targetDate, timeRange.start(), timeRange.end())
                    : CollectNewsRequest.of(targetDate);
            collectResult = collector.collect(request);
        } catch (Exception e) {
            log.error("Collection failed", e);
            executionLog.addError(toExecutionError("collect", e));
            executionLog.markFailed(KstDateTimeUtil.now());
            executionTracker.recordExecution(executionLog);
            return executionLog;
        }

        if (collectResult.articles().isEmpty()) {
            log.info("No articles collected, finishing successfully");
            executionLog.markSuccess(KstDateTimeUtil.now());
            return executionLog;
        }

        // 1.5 Validate collect result
        PipelineDataValidator.CollectValidationResult collectValidation =
                validator.validateCollectResult(collectResult, targetDate);
        for (PipelineDataValidator.ValidationWarning w : collectValidation.warnings()) {
            executionLog.addError(new ExecutionError(w.stage(), "ANALYZE_VALIDATION_ERROR", w.message(), false, null));
        }

        List<Article> validArticles = collectValidation.validArticles();
        if (validArticles.isEmpty()) {
            log.warn("No valid articles after validation");
            executionLog.addError(new ExecutionError("collect", "COLLECT_NO_ARTICLES",
                    "No valid articles after validation", false, null));
            executionLog.markFailed(KstDateTimeUtil.now());
            executionTracker.recordExecution(executionLog);
            return executionLog;
        }

        executionLog.setCollectedArticleCount(validArticles.size());

        // 1.6 Persist articles + Teacher classification
        articlePersistenceService.saveAll(validArticles);

        List<Article> teacherFiltered = validArticles;
        if (appProperties.teacher() != null && appProperties.teacher().enabled()) {
            teacherFiltered = applyTeacherClassification(validArticles);
            log.info("Teacher classification: before={}, after={}", validArticles.size(), teacherFiltered.size());

            if (teacherFiltered.isEmpty()) {
                teacherFiltered = validArticles; // fallback: use all if teacher filters everything
                log.warn("Teacher filtered all articles, falling back to all valid articles");
            }
        }

        // 1.65 Embedding (non-blocking, failures don't stop pipeline)
        if (embeddingService != null && appProperties.embedding() != null && appProperties.embedding().enabled()) {
            embeddingService.embedAll(teacherFiltered);
        }

        // 1.7 Apply relevance scoring and diversity selection
        int minRelevance = appProperties.diversity().minPersonalFinanceRelevance();
        RelevanceScorer.RelevanceScoringResult relevanceResult =
                relevanceScorer.score(teacherFiltered, minRelevance);
        DiversitySelector.DiversitySelectionResult diversityResult =
                diversitySelector.select(relevanceResult.filtered(), relevanceResult.scores(),
                        DiversitySelector.DiversityOptions.defaults());

        List<Article> articlesForAnalysis = diversityResult.selected();

        log.info("Filtering stats: teacherFiltered={}, relevance_passed={}, diversity_selected={}",
                teacherFiltered.size(), relevanceResult.filtered().size(), articlesForAnalysis.size());

        // Fallback: if all filtered out, use top teacher-filtered articles
        if (articlesForAnalysis.isEmpty() && !teacherFiltered.isEmpty()) {
            articlesForAnalysis = teacherFiltered.subList(0,
                    Math.min(teacherFiltered.size(), openAiProperties.maxSelectedNews()));
        }

        if (articlesForAnalysis.isEmpty()) {
            executionLog.markSuccess(KstDateTimeUtil.now());
            return executionLog;
        }

        // 2. Analyze
        AudienceProfile audience = buildAudienceProfile();
        String briefingTitle = timeRange != null
                ? KstDateTimeUtil.formatHourlyBriefingTitle(targetDate, timeRange.hour())
                : null;

        Integer targetHour = timeRange != null ? timeRange.hour() : null;

        AnalyzeNewsResult analyzeResult;
        try {
            analyzeResult = analyzer.analyze(new AnalyzeNewsRequest(
                    articlesForAnalysis, targetDate,
                    openAiProperties.maxSelectedNews(), audience, briefingTitle, targetHour));
        } catch (Exception e) {
            log.error("Analysis failed", e);
            executionLog.addError(toExecutionError("analyze", e));
            executionLog.markFailed(KstDateTimeUtil.now());
            executionTracker.recordExecution(executionLog);
            return executionLog;
        }

        // 2.5 Validate analyze result
        PipelineDataValidator.AnalyzeValidationResult analyzeValidation =
                validator.validateAnalyzeResult(analyzeResult, targetDate);
        for (PipelineDataValidator.ValidationWarning w : analyzeValidation.warnings()) {
            executionLog.addError(new ExecutionError(w.stage(), "ANALYZE_VALIDATION_ERROR", w.message(), false, null));
        }

        if (!analyzeValidation.valid()) {
            log.warn("No valid news in briefing after validation");
            executionLog.addError(new ExecutionError("analyze", "ANALYZE_EMPTY_INPUT",
                    "No valid news in briefing after validation", false, null));
            executionLog.markFailed(KstDateTimeUtil.now());
            executionTracker.recordExecution(executionLog);
            return executionLog;
        }

        executionLog.setSelectedNewsCount(analyzeResult.briefing().news().size());

        // 2.7 Save article analyses to DB (fail-safe)
        saveArticleAnalyses(analyzeResult.briefing());

        // 3. Publish
        PublishBriefingResult publishResult;
        try {
            publishResult = publisher.publish(new PublishBriefingRequest(
                    analyzeResult.briefing(), appProperties.dryRun()));
        } catch (Exception e) {
            log.error("Publishing failed", e);
            executionLog.addError(toExecutionError("publish", e));
            executionLog.markFailed(KstDateTimeUtil.now());
            executionTracker.recordExecution(executionLog);
            return executionLog;
        }

        // Record publish channel results
        for (PublishChannelResult r : publishResult.results()) {
            if (r.status() == PublishChannelResult.Status.FAILED) {
                executionLog.addError(new ExecutionError("publish",
                        r.errorCode() != null ? r.errorCode() : "PUBLISH_CHANNEL_ERROR",
                        r.errorMessage() != null ? r.errorMessage() : r.channel() + " publish failed",
                        false, null));
            } else if (r.status() == PublishChannelResult.Status.SKIPPED && r.errorCode() != null) {
                executionLog.addError(new ExecutionError("publish",
                        r.errorCode(),
                        r.errorMessage() != null ? r.errorMessage() : r.channel() + " publish skipped",
                        false, null));
            }
        }

        // Determine final status
        boolean allSucceeded = publishResult.results().stream()
                .allMatch(r -> r.status() == PublishChannelResult.Status.SUCCESS
                        || r.status() == PublishChannelResult.Status.SKIPPED);
        boolean allFailed = publishResult.results().stream()
                .allMatch(r -> r.status() == PublishChannelResult.Status.FAILED);

        if (allFailed) {
            executionLog.addError(new ExecutionError("publish",
                    ErrorCode.PUBLISH_ALL_CHANNELS_FAILED.name(),
                    "모든 발행 채널이 실패했습니다.", false, null));
            executionLog.markFailed(KstDateTimeUtil.now());
        } else if (allSucceeded) {
            executionLog.markSuccess(KstDateTimeUtil.now());
        } else {
            executionLog.markPartialSuccess(KstDateTimeUtil.now());
        }

        executionTracker.recordExecution(executionLog);
        log.info("Pipeline completed: executionId={}, status={}", executionId, executionLog.getStatus());
        return executionLog;
    }

    private void saveArticleAnalyses(Briefing briefing) {
        for (AnalyzedNews news : briefing.news()) {
            try {
                String primaryArticleId = news.sources().stream()
                        .filter(s -> s.isPrimary())
                        .map(s -> s.articleId())
                        .findFirst()
                        .orElse(news.sources().isEmpty() ? null : news.sources().get(0).articleId());

                if (primaryArticleId == null) continue;

                ArticleAnalysisEntity entity = new ArticleAnalysisEntity();
                entity.setArticleId(primaryArticleId);
                entity.setBriefingId(briefing.id());
                entity.setAnalysisJson(objectMapper.writeValueAsString(news));
                entity.setModelName(briefing.metadata().modelName());
                entity.setPromptVersion(briefing.metadata().promptVersion());
                articleAnalysisRepository.save(entity);
            } catch (Exception e) {
                log.warn("Failed to save article analysis for news {}: {}", news.id(), e.getMessage());
            }
        }
    }

    private List<Article> applyTeacherClassification(List<Article> articles) {
        String promptVersion = appProperties.teacher().promptVersion();
        List<Article> relevant = new ArrayList<>();

        for (Article article : articles) {
            try {
                TeacherLabelResponse response = teacherClassifier.classify(article);
                saveTeacherLabel(article.id(), response, promptVersion);

                if ("RELEVANT".equals(response.label()) || "UNCERTAIN".equals(response.label())) {
                    relevant.add(article);
                }
            } catch (Exception e) {
                log.warn("Teacher classification failed for article {}, including as RELEVANT: {}",
                        article.id(), e.getMessage());
                relevant.add(article); // fail-open: 분류 실패 시 포함
            }
        }
        return relevant;
    }

    private void saveTeacherLabel(String articleId, TeacherLabelResponse response, String promptVersion) {
        try {
            TeacherLabelEntity entity = new TeacherLabelEntity();
            entity.setArticleId(articleId);
            entity.setLabel(response.label());
            entity.setConfidence(response.confidence());
            entity.setReason(response.reason());
            entity.setAffectedAreas(response.affectedAreas() != null
                    ? toJsonArray(response.affectedAreas()) : null);
            entity.setSeverity(response.severity());
            entity.setNeedsFollowUp(response.needsFollowUp());
            entity.setUsableForTraining(response.usableForTraining());
            entity.setTeacherModelProvider("openai");
            entity.setTeacherModelName(openAiProperties.model());
            entity.setTeacherPromptVersion(promptVersion);
            entity.setTeacherTemperature(openAiProperties.temperature());
            entity.setLabeledAt(OffsetDateTime.now());
            teacherLabelRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to save teacher label for article {}: {}", articleId, e.getMessage());
        }
    }

    private AudienceProfile buildAudienceProfile() {
        AppProperties.AudienceProperties aud = appProperties.audience();
        List<NewsCategory> interests = aud.interests().stream()
                .map(NewsCategory::fromValue)
                .toList();
        return new AudienceProfile(aud.economicKnowledgeLevel(), interests, aud.contextNotes());
    }

    private static String toJsonArray(List<String> items) {
        if (items == null || items.isEmpty()) return "[]";
        return "[" + items.stream()
                .map(s -> "\"" + s.replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",")) + "]";
    }

    private ExecutionError toExecutionError(String stage, Exception e) {
        if (e instanceof BriefingException be) {
            return new ExecutionError(stage, be.getErrorCode().name(),
                    be.getMessage(), be.getErrorCode().isRetryable(), null);
        }
        return new ExecutionError(stage, "SYSTEM_UNEXPECTED",
                e.getMessage() != null ? e.getMessage() : "Unexpected error",
                false, null);
    }
}
