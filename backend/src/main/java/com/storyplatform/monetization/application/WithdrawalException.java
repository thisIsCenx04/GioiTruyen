package com.storyplatform.monetization.application;

public final class WithdrawalException extends RuntimeException {

    private final Kind kind;

    public WithdrawalException(String message, Kind kind) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        INVALID,
        FORBIDDEN,
        DESTINATION_UNAVAILABLE,
        INSUFFICIENT_BALANCE,
        CONFLICT
    }
}
