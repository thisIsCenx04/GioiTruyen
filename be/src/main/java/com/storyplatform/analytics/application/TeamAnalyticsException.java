package com.storyplatform.analytics.application;

public final class TeamAnalyticsException extends RuntimeException {

    private final Kind kind;

    public TeamAnalyticsException(String message, Kind kind) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        INVALID,
        FORBIDDEN
    }
}
