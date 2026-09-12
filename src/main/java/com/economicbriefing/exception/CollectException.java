package com.economicbriefing.exception;

public class CollectException extends BriefingException {

    private final String sourceName;

    public CollectException(ErrorCode errorCode, String sourceName) {
        super(errorCode, "collect");
        this.sourceName = sourceName;
    }

    public CollectException(ErrorCode errorCode, String sourceName, Throwable cause) {
        super(errorCode, "collect", cause);
        this.sourceName = sourceName;
    }

    public String getSourceName() {
        return sourceName;
    }
}
