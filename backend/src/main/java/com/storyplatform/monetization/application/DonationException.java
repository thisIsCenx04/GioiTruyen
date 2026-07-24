package com.storyplatform.monetization.application;

public final class DonationException extends RuntimeException {

    private final Kind kind;

    public DonationException(String message, Kind kind) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        INVALID,
        TEAM_NOT_FOUND,
        INSUFFICIENT_BALANCE,
        CONFLICT
    }
}
