package com.economicbriefing.pipeline;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.execution.ExecutionLog;
import com.economicbriefing.domain.execution.PublicationDecision;

/**
 * Records pipeline executions so operators can see what ran, and so a restart
 * does not lose duplicate-run protection.
 */
public interface ExecutionTracker {

    PublicationDecision checkDuplicate(String dedupeKey);

    void startRun(String runId, String dedupeKey, String triggerType, OffsetDateTime startedAt);

    void log(String runId, String level, String step, String eventCode, String message);

    void recordItems(String runId, List<Article> articles);

    void markAnalyzed(String runId, Set<String> articleUrls);

    void finishRun(String runId, String dedupeKey, ExecutionLog log);
}
