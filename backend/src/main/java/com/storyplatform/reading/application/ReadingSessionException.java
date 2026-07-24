package com.storyplatform.reading.application;

public final class ReadingSessionException extends RuntimeException {

    private final String code;
    private final Kind kind;
    private final long retryAfterSeconds;

    public ReadingSessionException(
            String code,
            String message,
            Kind kind
    ) {
        this(code, message, kind, 0);
    }

    public ReadingSessionException(
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
