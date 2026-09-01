package com.economicbriefing.analyzer.openai.dto;

import java.util.List;

import com.economicbriefing.economicflow.EventStatus;
import com.economicbriefing.economicflow.EventType;
import com.economicbriefing.economicflow.MilestonePeriodUnit;
import com.economicbriefing.economicflow.MilestoneType;

public record ArticleAnalyzerDraftResponse(List<DraftArticle> articles) {

    public record DraftArticle(
            String articleId,
            List<DraftIssue> issues,
            List<EventCandidateDraft> eventCandidates,
            List<FlowNode> flowNodes,
            List<FlowClaim> flowClaims) {}

    public record FlowNode(String candidateKey, String text) {}
    public record FlowClaim(String from, String to, FlowRelationType relationType) {}
    public enum FlowRelationType { CAUSE, PURPOSE, RESPONSE, CONDITION }

    public record EventCandidateDraft(
            EventType eventType,
            String title,
            String subject,
            String subjectKey,
            String eventDate,
            String previousState,
            String newState,
            EventStatus status,
            String region,
            List<String> topicKeys,
            List<String> newTopicCandidates,
            String evidenceText,
            MilestoneType milestoneType,
            Integer milestonePeriodValue,
            MilestonePeriodUnit milestonePeriodUnit,
            String milestoneReferenceDate,
            String candidateKey,
            com.economicbriefing.economicflow.NodeKind nodeKind,
            String scopeKey,
            String slotKey,
            String valueKey) {}

    public record DraftIssue(
            String name,
            List<String> mainFacts,
            List<ArticleAnalysisResponse.Change> changes,
            List<RelationCandidate> relationCandidates,
            List<ArticleAnalysisResponse.Statement> statements,
            List<String> keyTerms) {}

    public record RelationCandidate(String evidence, List<AtomicRelation> atomicRelations) {}

    public record AtomicRelation(
            String from,
            String to,
            ArticleAnalysisResponse.RelationType relationType,
            ArticleAnalysisResponse.StatementType evidenceType,
            String speaker,
            boolean storeInEconomicFlow,
            String fromCandidateKey,
            String toCandidateKey) {}
}
