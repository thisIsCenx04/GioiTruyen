package com.storyplatform.monetization.application;

public final class RewardException extends RuntimeException {

    private final Kind kind;

    public RewardException(String message, Kind kind) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        INVALID,
        FORBIDDEN,
        CONFLICT
    }
}
