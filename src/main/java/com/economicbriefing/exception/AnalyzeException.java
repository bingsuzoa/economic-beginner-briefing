package com.economicbriefing.exception;

public class AnalyzeException extends BriefingException {

    public AnalyzeException(ErrorCode errorCode) {
        super(errorCode, "analyze");
    }

    public AnalyzeException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, "analyze", cause);
    }
}
