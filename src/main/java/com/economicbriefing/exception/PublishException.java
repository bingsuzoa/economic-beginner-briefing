package com.economicbriefing.exception;

public class PublishException extends BriefingException {

    private final String channel;

    public PublishException(ErrorCode errorCode, String channel) {
        super(errorCode, "publish");
        this.channel = channel;
    }

    public PublishException(ErrorCode errorCode, String channel, Throwable cause) {
        super(errorCode, "publish", cause);
        this.channel = channel;
    }

    public String getChannel() {
        return channel;
    }
}
