package com.economicbriefing.pipeline;

import com.economicbriefing.domain.execution.ExecutionLog;
import com.economicbriefing.domain.execution.PublicationDecision;

public interface ExecutionTracker {

    PublicationDecision checkDuplicate(String dedupeKey);

    void recordExecution(ExecutionLog log);

    ExecutionLog getLastExecution(String dedupeKey);
}
