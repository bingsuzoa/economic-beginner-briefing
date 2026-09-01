package com.economicbriefing.economicflow;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EventCandidateValidatorTest {
    private final EventCandidateValidator validator = new EventCandidateValidator();

    @Test
    void validatesPeriodAndRecordMilestonesButKeepsDailyChangeNonMilestone() {
        assertDoesNotThrow(() -> validator.validate(candidate(
                EventType.INDICATOR_MILESTONE, MilestoneType.PERIOD_LOW, 13, MilestonePeriodUnit.MONTH)));
        assertDoesNotThrow(() -> validator.validate(candidate(
                EventType.INDICATOR_MILESTONE, MilestoneType.RECORD_HIGH, null, null)));
        assertDoesNotThrow(() -> validator.validate(candidate(
                EventType.INDICATOR_MILESTONE, MilestoneType.LARGEST_DECREASE, null, null)));
        assertDoesNotThrow(() -> validator.validate(candidate(
                EventType.INDICATOR_CHANGE, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(candidate(
                EventType.INDICATOR_MILESTONE, null, null, null)));
    }

    @Test
    void acceptsAnnouncedCorporateRestructuring() {
        EventCandidate candidate = new EventCandidate("article", EventType.CORPORATE_RESTRUCTURING,
                "카카오 인적분할 발표", "카카오", "KAKAO", LocalDate.of(2026, 8, 26),
                null, "인적분할 발표", EventStatus.ANNOUNCED, "KR", List.of("CAPITAL_MARKET"),
                List.of(), "카카오가 인적분할 계획을 발표했다.");

        assertDoesNotThrow(() -> validator.validate(candidate));
    }

    private EventCandidate candidate(EventType eventType, MilestoneType milestoneType,
            Integer period, MilestonePeriodUnit unit) {
        return new EventCandidate("article", eventType, "지표 사건", "지표", "USD_KRW",
                LocalDate.of(2026, 8, 24), null, "1,370원대", EventStatus.CONFIRMED, null,
                List.of("USD_KRW"), List.of(), "원문 근거", milestoneType, period, unit, null);
    }
}
