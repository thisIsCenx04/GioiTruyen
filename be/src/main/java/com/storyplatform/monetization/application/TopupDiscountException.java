package com.storyplatform.monetization.application;

public final class TopupDiscountException extends RuntimeException {

    private final Kind kind;

    public TopupDiscountException(String message, Kind kind) {
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
