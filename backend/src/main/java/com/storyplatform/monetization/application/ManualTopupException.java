package com.storyplatform.monetization.application;

public final class ManualTopupException extends RuntimeException {

    private final Kind kind;

    public ManualTopupException(String message, Kind kind) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        INVALID,
        FORBIDDEN,
        NOT_FOUND,
        CONFLICT
    }
}
