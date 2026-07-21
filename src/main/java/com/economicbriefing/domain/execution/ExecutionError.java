package com.economicbriefing.domain.execution;

public record ExecutionError(
    String stage,
    String code,
    String message,
    boolean retryable,
    String sourceName
) {}
