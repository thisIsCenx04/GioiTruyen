package com.storyplatform.monetization.application;

public final class ReferralException extends RuntimeException {

    private final Kind kind;

    public ReferralException(String message, Kind kind) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        INVALID,
        NOT_FOUND,
        CONFLICT,
        RATE_LIMITED
    }
}
