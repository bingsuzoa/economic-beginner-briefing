package com.economicbriefing.pipeline;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

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

        executionTracker.startRun(executionId, dedupeKey, options.triggerType(), executionLog.getStartedAt());

        // Every exit path below must land in finishRun, or the run row stays RUNNING forever.
        try {
            return execute(executionId, dedupeKey, targetDate, timeRange, executionLog);
        } catch (RuntimeException e) {
            log.error("Pipeline aborted unexpectedly", e);
            executionLog.addError(toExecutionError("system", e));
            executionLog.markFailed(KstDateTimeUtil.now());
            return executionLog;
        } finally {
            executionTracker.finishRun(executionId, dedupeKey, executionLog);
            log.info("Pipeline completed: executionId={}, status={}", executionId, executionLog.getStatus());
        }
    }

    private ExecutionLog execute(String runId, String dedupeKey, LocalDate targetDate,
                                 KstDateTimeUtil.TimeRange timeRange, ExecutionLog executionLog) {

        // 1. Collect
        CollectNewsResult collectResult;
        try {
            executionTracker.log(runId, "INFO", "COLLECT", "COLLECT_START", "뉴스 수집을 시작합니다.");
            CollectNewsRequest request = timeRange != null
                    ? CollectNewsRequest.of(targetDate, timeRange.start(), timeRange.end())
                    : CollectNewsRequest.of(targetDate);
            collectResult = collector.collect(request);
        } catch (Exception e) {
            log.error("Collection failed", e);
            executionLog.addError(toExecutionError("collect", e));
            executionLog.markFailed(KstDateTimeUtil.now());
            executionTracker.log(runId, "ERROR", "COLLECT", "COLLECT_FAILED", String.valueOf(e.getMessage()));
            return executionLog;
        }

        if (collectResult.articles().isEmpty()) {
            log.info("No articles collected, finishing successfully");
            executionTracker.log(runId, "INFO", "COLLECT", "COLLECT_EMPTY", "수집된 기사가 없습니다.");
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
            executionTracker.log(runId, "ERROR", "COLLECT", "COLLECT_NO_ARTICLES",
                    "검증을 통과한 기사가 없습니다.");
            return executionLog;
        }

        executionLog.setCollectedArticleCount(validArticles.size());
        executionTracker.recordItems(runId, validArticles);
        executionTracker.log(runId, "INFO", "COLLECT", "COLLECT_DONE",
                validArticles.size() + "건의 기사를 수집했습니다.");

        // 1.6 Persist articles
        articlePersistenceService.saveAll(validArticles);

        // Teacher/embedding/relevance/diversity skipped — GPT-4o handles selection directly
        List<Article> articlesForAnalysis = validArticles;
        log.info("Passing {} articles directly to analysis (filtering delegated to GPT-4o)",
                articlesForAnalysis.size());

        if (articlesForAnalysis.isEmpty()) {
            executionTracker.log(runId, "WARN", "ANALYZE", "ANALYZE_NO_CANDIDATES",
                    "분석할 후보 기사가 없습니다.");
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
            executionTracker.log(runId, "INFO", "ANALYZE", "ANALYZE_START",
                    articlesForAnalysis.size() + "건을 AI 분석합니다.");
            analyzeResult = analyzer.analyze(new AnalyzeNewsRequest(
                    articlesForAnalysis, targetDate,
                    openAiProperties.maxSelectedNews(), audience, briefingTitle, targetHour));
        } catch (Exception e) {
            log.error("Analysis failed", e);
            executionLog.addError(toExecutionError("analyze", e));
            executionLog.markFailed(KstDateTimeUtil.now());
            executionTracker.log(runId, "ERROR", "ANALYZE", "ANALYZE_FAILED", String.valueOf(e.getMessage()));
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
            executionTracker.log(runId, "ERROR", "ANALYZE", "ANALYZE_EMPTY_INPUT",
                    "검증을 통과한 분석 결과가 없습니다.");
            return executionLog;
        }

        // 2.6 Filter out irrelevant news (accidents, disasters, etc.)
        Briefing filteredBriefing = filterIrrelevantNews(analyzeResult.briefing());
        if (filteredBriefing.news().isEmpty()) {
            log.warn("All news filtered out as irrelevant");
            executionTracker.log(runId, "WARN", "ANALYZE", "ANALYZE_ALL_FILTERED",
                    "모든 뉴스가 무관한 내용으로 필터링되었습니다.");
        }

        executionLog.setSelectedNewsCount(filteredBriefing.news().size());

        // 2.7 Save article analyses to DB — this is what the public API serves
        saveArticleAnalyses(filteredBriefing);
        executionTracker.markAnalyzed(runId, analyzedUrls(analyzeResult.briefing()));
        executionTracker.log(runId, "INFO", "ANALYZE", "ANALYZE_DONE",
                analyzeResult.briefing().news().size() + "건의 분석 결과를 저장했습니다.");

        executionLog.markSuccess(KstDateTimeUtil.now());
        return executionLog;
    }

    private Set<String> analyzedUrls(Briefing briefing) {
        Set<String> urls = new HashSet<>();
        for (AnalyzedNews news : briefing.news()) {
            news.sources().forEach(source -> urls.add(source.url()));
        }
        return urls;
    }

    private Briefing filterIrrelevantNews(Briefing briefing) {
        List<AnalyzedNews> relevant = briefing.news().stream()
                .filter(news -> {
                    // Filter out accidents, disasters, local incidents
                    if (news.category() == NewsCategory.OTHER && news.importance() <= 1) {
                        log.info("Filtered out low-importance 'other' category: {}", news.easyTitle());
                        return false;
                    }

                    // Filter out news explicitly stating no economic impact
                    String economicImpact = news.economicImpact() != null ? news.economicImpact() : "";

                    if (economicImpact.contains("경제적 영향과는 관련이 없") ||
                        economicImpact.contains("경제적 영향은 없")) {
                        log.info("Filtered out news with no economic impact: {}", news.easyTitle());
                        return false;
                    }

                    return true;
                })
                .toList();

        return new Briefing(
                briefing.id(),
                briefing.targetDate(),
                briefing.generatedAt(),
                briefing.title(),
                briefing.overallSummary(),
                relevant,  // filtered news list
                briefing.glossary(),
                briefing.metadata()
        );
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

    /**
     * Classifies articles with bounded concurrency. One article per API call, so running
     * these serially cost ~2 minutes for 80 articles; the bound keeps us under the rate limit.
     * Order of the returned list follows the input.
     */
    private List<Article> applyTeacherClassification(List<Article> articles) {
        String promptVersion = appProperties.teacher().promptVersion();
        int concurrency = Math.max(1, appProperties.teacher().concurrency());
        Semaphore permits = new Semaphore(concurrency);

        List<Article> relevant = Collections.synchronizedList(new ArrayList<>());
        Set<String> relevantIds = ConcurrentHashMap.newKeySet();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (Article article : articles) {
                futures.add(executor.submit(() -> {
                    permits.acquireUninterruptibly();
                    try {
                        if (classifyOne(article, promptVersion)) {
                            relevantIds.add(article.id());
                        }
                    } finally {
                        permits.release();
                    }
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException e) {
                    log.warn("Teacher classification task failed: {}", e.getCause().getMessage());
                }
            }
        }

        // rebuild in input order
        for (Article article : articles) {
            if (relevantIds.contains(article.id())) {
                relevant.add(article);
            }
        }
        return relevant;
    }

    /** Returns true when the article should go on to analysis. */
    private boolean classifyOne(Article article, String promptVersion) {
        try {
            // Reuse an existing label for this prompt version: re-runs must not
            // duplicate rows or re-spend the OpenAI token budget.
            String label = teacherLabelRepository
                    .findByArticleIdAndTeacherPromptVersion(article.id(), promptVersion)
                    .map(TeacherLabelEntity::getLabel)
                    .orElse(null);

            if (label == null) {
                TeacherLabelResponse response = teacherClassifier.classify(article);
                saveTeacherLabel(article.id(), response, promptVersion);
                label = response.label();
            }

            return "RELEVANT".equals(label) || "UNCERTAIN".equals(label);
        } catch (Exception e) {
            log.warn("Teacher classification failed for article {}, including as RELEVANT: {}",
                    article.id(), e.getMessage());
            return true; // fail-open: 분류 실패 시 포함
        }
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
            entity.setTeacherModelName(appProperties.teacher().model());
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
