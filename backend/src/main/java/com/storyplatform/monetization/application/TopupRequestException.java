package com.storyplatform.monetization.application;

public final class TopupRequestException extends RuntimeException {

    private final Kind kind;

    public TopupRequestException(String message, Kind kind) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        INVALID,
        CONFLICT,
        NOT_FOUND
    }
}
