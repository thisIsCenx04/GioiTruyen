package com.storyplatform.moderation.application;

public final class CommunityReportException extends RuntimeException {

    private final String code;
    private final Kind kind;
    private final long retryAfterSeconds;

    public CommunityReportException(
            String code,
            String message,
            Kind kind
    ) {
        this(code, message, kind, 0);
    }

    public CommunityReportException(
            String code,
            String message,
            Kind kind,
            long retryAfterSeconds
    ) {
        super(message);
        this.code = code;
        this.kind = kind;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String code() {
        return code;
    }

    public Kind kind() {
        return kind;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public enum Kind {
        INVALID,
        NOT_FOUND,
        RATE_LIMITED,
        UNAVAILABLE
    }
}
