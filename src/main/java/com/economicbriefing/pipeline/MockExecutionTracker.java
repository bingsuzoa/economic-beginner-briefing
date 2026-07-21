package com.economicbriefing.pipeline;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.economicbriefing.domain.execution.ExecutionLog;
import com.economicbriefing.domain.execution.ExecutionStatus;
import com.economicbriefing.domain.execution.PublicationDecision;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "briefing.dry-run", havingValue = "true", matchIfMissing = true)
public class MockExecutionTracker implements ExecutionTracker {

    private final Map<String, ExecutionLog> executions = new ConcurrentHashMap<>();

    @Override
    public PublicationDecision checkDuplicate(String dedupeKey) {
        ExecutionLog last = executions.get(dedupeKey);
        if (last == null) {
            return PublicationDecision.PUBLISH;
        }
        if (last.getStatus() == ExecutionStatus.SUCCESS) {
            return PublicationDecision.SKIP_ALREADY_PUBLISHED;
        }
        return PublicationDecision.RETRY_PREVIOUS_FAILURE;
    }

    @Override
    public void recordExecution(ExecutionLog log) {
        executions.put(log.getTargetDate().toString(), log);
    }

    @Override
    public ExecutionLog getLastExecution(String dedupeKey) {
        return executions.get(dedupeKey);
    }

    public void clear() {
        executions.clear();
    }
}
