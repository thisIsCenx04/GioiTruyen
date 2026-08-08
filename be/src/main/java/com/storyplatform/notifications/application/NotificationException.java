package com.storyplatform.notifications.application;

public final class NotificationException extends RuntimeException {

    private final String code;
    private final Kind kind;

    public NotificationException(String code, String message, Kind kind) {
        super(message);
        this.code = code;
        this.kind = kind;
    }

    public String code() {
        return code;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        INVALID,
        NOT_FOUND,
        CONFLICT
    }
}
