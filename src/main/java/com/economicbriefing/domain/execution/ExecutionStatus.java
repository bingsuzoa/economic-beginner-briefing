package com.economicbriefing.domain.execution;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ExecutionStatus {
    RUNNING,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED;

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ExecutionStatus fromValue(String value) {
        return valueOf(value.toUpperCase());
    }
}
