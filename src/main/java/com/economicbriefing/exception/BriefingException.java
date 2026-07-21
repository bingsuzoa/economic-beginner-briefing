package com.economicbriefing.exception;

public class BriefingException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String stage;

    public BriefingException(ErrorCode errorCode, String stage) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.stage = stage;
    }

    public BriefingException(ErrorCode errorCode, String stage, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.stage = stage;
    }

    public BriefingException(ErrorCode errorCode, String stage, String detail) {
        super(errorCode.getMessage() + ": " + detail);
        this.errorCode = errorCode;
        this.stage = stage;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getStage() {
        return stage;
    }
}
