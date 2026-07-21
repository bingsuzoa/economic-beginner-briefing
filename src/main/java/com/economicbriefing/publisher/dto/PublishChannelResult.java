package com.economicbriefing.publisher.dto;

public record PublishChannelResult(
    String channel,
    Status status,
    String externalId,
    String errorCode,
    String errorMessage
) {
    public enum Status {
        SUCCESS, SKIPPED, FAILED
    }

    public static PublishChannelResult success(String channel, String externalId) {
        return new PublishChannelResult(channel, Status.SUCCESS, externalId, null, null);
    }

    public static PublishChannelResult skipped(String channel, String externalId) {
        return new PublishChannelResult(channel, Status.SKIPPED, externalId, null, null);
    }

    public static PublishChannelResult skipped(String channel, String externalId,
                                                String errorCode, String errorMessage) {
        return new PublishChannelResult(channel, Status.SKIPPED, externalId, errorCode, errorMessage);
    }

    public static PublishChannelResult failed(String channel, String errorCode, String errorMessage) {
        return new PublishChannelResult(channel, Status.FAILED, null, errorCode, errorMessage);
    }
}
