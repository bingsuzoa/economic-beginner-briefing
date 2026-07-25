package com.economicbriefing.pipeline;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import com.economicbriefing.admin.entity.PipelineItemEntity;
import com.economicbriefing.admin.entity.PipelineLogEntity;
import com.economicbriefing.admin.entity.PipelineRunEntity;
import com.economicbriefing.admin.repository.PipelineItemRepository;
import com.economicbriefing.admin.repository.PipelineLogRepository;
import com.economicbriefing.admin.repository.PipelineRunRepository;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.execution.ExecutionError;
import com.economicbriefing.domain.execution.ExecutionLog;
import com.economicbriefing.domain.execution.PublicationDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists run history to pipeline_runs / pipeline_logs / pipeline_items.
 * Tracking must never break the pipeline, so every write is fail-safe.
 */
@Component
public class JpaExecutionTracker implements ExecutionTracker {

    private static final Logger log = LoggerFactory.getLogger(JpaExecutionTracker.class);

    private final PipelineRunRepository runRepo;
    private final PipelineLogRepository logRepo;
    private final PipelineItemRepository itemRepo;

    public JpaExecutionTracker(PipelineRunRepository runRepo,
                               PipelineLogRepository logRepo,
                               PipelineItemRepository itemRepo) {
        this.runRepo = runRepo;
        this.logRepo = logRepo;
        this.itemRepo = itemRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public PublicationDecision checkDuplicate(String dedupeKey) {
        return runRepo.findFirstByDedupeKeyOrderByStartedAtDesc(dedupeKey)
                .map(run -> "SUCCESS".equals(run.getStatus())
                        ? PublicationDecision.SKIP_ALREADY_PUBLISHED
                        : PublicationDecision.RETRY_PREVIOUS_FAILURE)
                .orElse(PublicationDecision.PUBLISH);
    }

    @Override
    @Transactional
    public void startRun(String runId, String dedupeKey, String triggerType, OffsetDateTime startedAt) {
        try {
            PipelineRunEntity run = new PipelineRunEntity();
            run.setId(runId);
            run.setDedupeKey(dedupeKey);
            run.setTriggerType(triggerType);
            run.setStartedAt(startedAt);
            run.setStatus("RUNNING");
            run.setCurrentStep("INIT");
            runRepo.save(run);
        } catch (Exception e) {
            log.warn("Failed to record run start {}: {}", runId, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void log(String runId, String level, String step, String eventCode, String message) {
        try {
            PipelineLogEntity entry = new PipelineLogEntity();
            entry.setRunId(runId);
            entry.setLevel(level);
            entry.setStep(step);
            entry.setEventCode(eventCode);
            entry.setMessage(message != null ? message : "");
            logRepo.save(entry);

            runRepo.findById(runId).ifPresent(run -> {
                run.setCurrentStep(step);
                runRepo.save(run);
                logOperational(run.getTriggerType(), eventCode, message);
            });
        } catch (Exception e) {
            log.warn("Failed to write pipeline log for {}: {}", runId, e.getMessage());
        }
    }

    /**
     * Operator-facing progress line. Every step already funnels through {@link #log}, and the
     * run row (loaded just above) carries the trigger type, so the prefix costs no extra query.
     * Unknown event codes stay silent — the DB row is still the complete record.
     */
    private static void logOperational(String triggerType, String eventCode, String message) {
        String source = "SCHEDULER".equals(triggerType) ? "Scheduler" : "Admin";
        String line = switch (eventCode == null ? "" : eventCode) {
            case "COLLECT_DONE" -> "RSS collected : " + digitsIn(message);
            case "COLLECT_EMPTY" -> "RSS collected : 0";
            case "TEACHER_DONE" -> "Teacher completed";
            case "EMBED_DONE" -> "Embedding completed";
            case "EMBED_SKIPPED" -> "Embedding skipped (disabled)";
            case "ANALYZE_DONE" -> "Analyze completed";
            case "COLLECT_FAILED", "COLLECT_NO_ARTICLES",
                 "ANALYZE_FAILED", "ANALYZE_EMPTY_INPUT" -> "Failed at " + eventCode;
            default -> null;
        };

        if (line != null) {
            log.info("[{}] {}", source, line);
        }
    }

    /** Pulls the count out of the Korean step message ("88건의 기사를 수집했습니다."). */
    private static String digitsIn(String message) {
        if (message == null) return "?";
        StringBuilder digits = new StringBuilder();
        for (char c : message.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (!digits.isEmpty()) {
                break;
            }
        }
        return digits.isEmpty() ? "?" : digits.toString();
    }

    @Override
    @Transactional
    public void recordItems(String runId, List<Article> articles) {
        try {
            List<PipelineItemEntity> items = articles.stream().map(article -> {
                PipelineItemEntity item = new PipelineItemEntity();
                item.setRunId(runId);
                item.setArticleUrl(article.url());
                item.setNormalizedUrl(article.url());
                item.setSource(article.sourceName());
                item.setOriginalTitle(article.title());
                item.setOriginalSummary(article.summary());
                item.setPublishedAt(article.publishedAt());
                item.setCategory(article.categories() != null && !article.categories().isEmpty()
                        ? article.categories().get(0).name() : null);
                item.setDuplicateStatus("UNIQUE");
                item.setAnalysisStatus("PENDING");
                return item;
            }).toList();

            itemRepo.saveAll(items);
        } catch (Exception e) {
            log.warn("Failed to record pipeline items for {}: {}", runId, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void markAnalyzed(String runId, Set<String> articleUrls) {
        try {
            for (PipelineItemEntity item : itemRepo.findByRunIdOrderByIdAsc(runId)) {
                item.setAnalysisStatus(articleUrls.contains(item.getArticleUrl()) ? "SUCCESS" : "SKIPPED");
            }
        } catch (Exception e) {
            log.warn("Failed to mark analyzed items for {}: {}", runId, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void finishRun(String runId, String dedupeKey, ExecutionLog executionLog) {
        try {
            PipelineRunEntity run = runRepo.findById(runId).orElseGet(() -> {
                PipelineRunEntity created = new PipelineRunEntity();
                created.setId(runId);
                created.setTriggerType("UNKNOWN");
                created.setStartedAt(executionLog.getStartedAt());
                return created;
            });

            OffsetDateTime finishedAt = executionLog.getCompletedAt() != null
                    ? executionLog.getCompletedAt()
                    : OffsetDateTime.now();

            run.setDedupeKey(dedupeKey);
            run.setStatus(executionLog.getStatus().name());
            run.setFinishedAt(finishedAt);
            run.setDurationMs((int) java.time.Duration
                    .between(executionLog.getStartedAt(), finishedAt).toMillis());
            run.setCurrentStep("DONE");
            run.setCollectedCount(executionLog.getCollectedArticleCount());
            run.setAnalysisSuccessCount(executionLog.getSelectedNewsCount());
            run.setTotalFailureCount(executionLog.getErrors().size());

            if (!executionLog.getErrors().isEmpty()) {
                ExecutionError first = executionLog.getErrors().get(0);
                run.setErrorCode(first.code());
                run.setErrorMessage(first.message());
            }

            runRepo.save(run);
        } catch (Exception e) {
            log.warn("Failed to record run completion {}: {}", runId, e.getMessage());
        }
    }
}
