package com.economicbriefing.domain.analysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NewsEvidenceStatus {
    CONFIRMED,
    PROPOSED,
    EXPECTED;

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static NewsEvidenceStatus fromValue(String value) {
        return valueOf(value.toUpperCase());
    }
}
