package com.economicbriefing.economicflow;

import java.time.LocalDate;
import java.util.List;

public record EventCandidate(
        String articleId,
        EventType eventType,
        String title,
        String subject,
        String subjectKey,
        LocalDate eventDate,
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
        LocalDate milestoneReferenceDate,
        String candidateKey,
        NodeKind nodeKind,
        String scopeKey,
        String slotKey,
        String valueKey) {

    public EventCandidate {
        topicKeys = topicKeys == null ? List.of() : List.copyOf(topicKeys);
        newTopicCandidates = newTopicCandidates == null ? List.of() : List.copyOf(newTopicCandidates);
    }

    public EventCandidate(String articleId, EventType eventType, String title, String subject,
            String subjectKey, LocalDate eventDate, String previousState, String newState,
            EventStatus status, String region, List<String> topicKeys,
            List<String> newTopicCandidates, String evidenceText) {
        this(articleId, eventType, title, subject, subjectKey, eventDate, previousState, newState,
                status, region, topicKeys, newTopicCandidates, evidenceText, null, null, null, null,
                null, null, null, null, null);
    }

    public EventCandidate(String articleId, EventType eventType, String title, String subject,
            String subjectKey, LocalDate eventDate, String previousState, String newState,
            EventStatus status, String region, List<String> topicKeys,
            List<String> newTopicCandidates, String evidenceText, MilestoneType milestoneType,
            Integer milestonePeriodValue, MilestonePeriodUnit milestonePeriodUnit,
            LocalDate milestoneReferenceDate) {
        this(articleId, eventType, title, subject, subjectKey, eventDate, previousState, newState,
                status, region, topicKeys, newTopicCandidates, evidenceText, milestoneType,
                milestonePeriodValue, milestonePeriodUnit, milestoneReferenceDate,
                null, null, null, null, null);
    }
}
