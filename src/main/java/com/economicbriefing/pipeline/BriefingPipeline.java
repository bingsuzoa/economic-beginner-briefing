package com.economicbriefing.pipeline;

import java.time.LocalDate;
import java.util.List;

import com.economicbriefing.analyzer.NewsAnalyzer;
import com.economicbriefing.analyzer.dto.AnalyzeNewsRequest;
import com.economicbriefing.analyzer.dto.AnalyzeNewsResult;
import com.economicbriefing.collector.NewsCollector;
import com.economicbriefing.collector.dto.CollectNewsRequest;
import com.economicbriefing.collector.dto.CollectNewsResult;
import com.economicbriefing.collector.filter.DiversitySelector;
import com.economicbriefing.collector.filter.RelevanceScorer;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.config.OpenAiProperties;
import com.economicbriefing.domain.analysis.AudienceProfile;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.NewsCategory;
import com.economicbriefing.domain.execution.ExecutionError;
import com.economicbriefing.domain.execution.ExecutionLog;
import com.economicbriefing.domain.execution.PublicationDecision;
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

    public BriefingPipeline(
            NewsCollector collector,
            NewsAnalyzer analyzer,
            BriefingPublisher publisher,
            ExecutionTracker executionTracker,
            PipelineDataValidator validator,
            RelevanceScorer relevanceScorer,
            DiversitySelector diversitySelector,
            AppProperties appProperties,
            OpenAiProperties openAiProperties) {
        this.collector = collector;
        this.analyzer = analyzer;
        this.publisher = publisher;
        this.executionTracker = executionTracker;
        this.validator = validator;
        this.relevanceScorer = relevanceScorer;
        this.diversitySelector = diversitySelector;
        this.appProperties = appProperties;
        this.openAiProperties = openAiProperties;
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

        // 1.7 Apply relevance scoring and diversity selection
        int minRelevance = appProperties.diversity().minPersonalFinanceRelevance();
        RelevanceScorer.RelevanceScoringResult relevanceResult =
                relevanceScorer.score(validArticles, minRelevance);
        DiversitySelector.DiversitySelectionResult diversityResult =
                diversitySelector.select(relevanceResult.filtered(), relevanceResult.scores(),
                        DiversitySelector.DiversityOptions.defaults());

        List<Article> articlesForAnalysis = diversityResult.selected();

        log.info("Filtering stats: validated={}, relevance_passed={}, diversity_selected={}",
                validArticles.size(), relevanceResult.filtered().size(), articlesForAnalysis.size());

        // Fallback: if all filtered out, use top valid articles
        if (articlesForAnalysis.isEmpty() && !validArticles.isEmpty()) {
            articlesForAnalysis = validArticles.subList(0,
                    Math.min(validArticles.size(), openAiProperties.maxSelectedNews()));
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

        AnalyzeNewsResult analyzeResult;
        try {
            analyzeResult = analyzer.analyze(new AnalyzeNewsRequest(
                    articlesForAnalysis, targetDate,
                    openAiProperties.maxSelectedNews(), audience, briefingTitle));
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

    private AudienceProfile buildAudienceProfile() {
        AppProperties.AudienceProperties aud = appProperties.audience();
        List<NewsCategory> interests = aud.interests().stream()
                .map(NewsCategory::fromValue)
                .toList();
        return new AudienceProfile(aud.economicKnowledgeLevel(), interests, aud.contextNotes());
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
