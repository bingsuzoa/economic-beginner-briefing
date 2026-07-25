package com.economicbriefing.exception;

import java.time.Duration;

public class AnalyzeException extends BriefingException {

    /** How long the server asked us to wait (429 Retry-After); null when unknown. */
    private final Duration retryAfter;

    public AnalyzeException(ErrorCode errorCode) {
        this(errorCode, (Duration) null);
    }

    public AnalyzeException(ErrorCode errorCode, Duration retryAfter) {
        super(errorCode, "analyze");
        this.retryAfter = retryAfter;
    }

    public AnalyzeException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, "analyze", cause);
        this.retryAfter = null;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
