package com.storyplatform.community.application;

public final class CommentException extends RuntimeException {

    private final String code;
    private final Kind kind;
    private final long retryAfterSeconds;

    public CommentException(String code, String message, Kind kind) {
        this(code, message, kind, 0);
    }

    public CommentException(
            String code,
            String message,
            Kind kind,
            long retryAfterSeconds
    ) {
        super(message);
        this.code = code;
        this.kind = kind;
        if (retryAfterSeconds < 0) {
            throw new IllegalArgumentException(
                    "retryAfterSeconds cannot be negative"
            );
        }
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
        CONFLICT,
        RATE_LIMITED,
        UNAVAILABLE
    }
}
