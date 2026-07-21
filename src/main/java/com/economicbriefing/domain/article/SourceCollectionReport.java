package com.economicbriefing.domain.article;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record SourceCollectionReport(
    String sourceName,
    CollectionStatus status,
    int collectedCount,
    int acceptedCount,
    String errorCode,
    String errorMessage,
    Integer rawCount,
    Integer dateFilteredCount,
    Integer qualityPassedCount,
    Integer deduplicatedCount,
    Long durationMs
) {
    public enum CollectionStatus {
        SUCCESS,
        PARTIAL,
        FAILED;

        @JsonValue
        public String toValue() {
            return name().toLowerCase();
        }

        @JsonCreator
        public static CollectionStatus fromValue(String value) {
            return valueOf(value.toUpperCase());
        }
    }
}
