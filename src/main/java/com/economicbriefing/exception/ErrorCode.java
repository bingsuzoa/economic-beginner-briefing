package com.economicbriefing.exception;

public enum ErrorCode {

    // Collect
    COLLECT_SOURCE_TIMEOUT("RSS feed timeout", true),
    COLLECT_SOURCE_UNAVAILABLE("Feed unreachable", true),
    COLLECT_PARSE_ERROR("RSS parsing failed", false),
    COLLECT_NO_ARTICLES("No articles after filtering", false),

    // Analyze
    ANALYZE_API_ERROR("OpenAI API error", true),
    ANALYZE_VALIDATION_ERROR("Response schema invalid", false),
    ANALYZE_TIMEOUT("OpenAI API timeout", true),
    ANALYZE_EMPTY_INPUT("No articles to analyze", false),

    // Publish
    PUBLISH_CHANNEL_ERROR("Channel publish failed", true),
    PUBLISH_ALL_CHANNELS_FAILED("All channels failed", false),
    PUBLISH_DUPLICATE("Briefing already exists", false),

    // System
    SYSTEM_CONFIG_ERROR("Config validation failed", false),
    SYSTEM_UNEXPECTED("Unexpected error", false),

    // Pipeline
    PIPELINE_ALREADY_RUNNING("Pipeline already running", false),
    RUN_NOT_FOUND("Pipeline run not found", false),
    ITEM_NOT_FOUND("Item not found", false),
    UNAUTHORIZED("Auth token invalid", false),
    DB_CONNECTION_ERROR("DB connection failed", true);

    private final String message;
    private final boolean retryable;

    ErrorCode(String message, boolean retryable) {
        this.message = message;
        this.retryable = retryable;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
