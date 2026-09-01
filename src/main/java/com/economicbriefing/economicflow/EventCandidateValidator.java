package com.economicbriefing.economicflow;

import org.springframework.stereotype.Component;

@Component
public class EventCandidateValidator {
    public void validate(EventCandidate candidate) {
        if (candidate == null || candidate.eventType() == null || candidate.status() == null
                || blank(candidate.articleId()) || blank(candidate.title()) || blank(candidate.subject())
                || blank(candidate.subjectKey()) || candidate.eventDate() == null || blank(candidate.evidenceText())) {
            throw new IllegalArgumentException("EventCandidate has missing required fields");
        }
        if (candidate.eventType() == EventType.POLICY_CHANGE
                && candidate.status() != EventStatus.CONFIRMED) {
            throw new IllegalArgumentException("Unconfirmed policy cannot be POLICY_CHANGE");
        }
        boolean milestone = candidate.eventType() == EventType.INDICATOR_MILESTONE;
        if (milestone && candidate.milestoneType() == null) {
            throw new IllegalArgumentException("INDICATOR_MILESTONE requires milestoneType");
        }
        if (!milestone && (candidate.milestoneType() != null || candidate.milestonePeriodValue() != null
                || candidate.milestonePeriodUnit() != null || candidate.milestoneReferenceDate() != null)) {
            throw new IllegalArgumentException("Milestone fields require INDICATOR_MILESTONE");
        }
        if ((candidate.milestonePeriodValue() == null) != (candidate.milestonePeriodUnit() == null)
                || candidate.milestonePeriodValue() != null && candidate.milestonePeriodValue() < 1) {
            throw new IllegalArgumentException("Milestone period value and unit must be a positive pair");
        }
        if (candidate.nodeKind() != null && blank(candidate.candidateKey())) {
            throw new IllegalArgumentException("Normalized node requires candidateKey");
        }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
