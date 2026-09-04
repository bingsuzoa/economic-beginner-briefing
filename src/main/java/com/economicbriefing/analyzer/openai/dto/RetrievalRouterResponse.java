package com.economicbriefing.analyzer.openai.dto;

import java.util.List;

public record RetrievalRouterResponse(List<ArticleRoute> articles) {

    public record ArticleRoute(String articleId, List<IssueRoute> issues) {}

    public record IssueRoute(String issueName, boolean needsRetrieval, List<RetrievalRequest> requests) {}

    public record RetrievalRequest(
            GapType gapType,
            String target,
            String query,
            String sourceReference,
            String reason,
            Priority priority,
            KnowledgeType knowledgeType) {}

    public enum GapType { TERM, WHY, SYSTEM, SIGNIFICANCE }

    public enum Priority { HIGH, MEDIUM, LOW }

    public enum KnowledgeType { PRINCIPLE, FLOW }
}
