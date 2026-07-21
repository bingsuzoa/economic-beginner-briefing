package com.economicbriefing.domain.execution;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PublicationDecision {
    PUBLISH,
    SKIP_ALREADY_PUBLISHED,
    RETRY_PREVIOUS_FAILURE;

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static PublicationDecision fromValue(String value) {
        return valueOf(value.toUpperCase());
    }
}
