package com.storyplatform.publishing.application;

public final class StoryDraftException extends RuntimeException {

    private final String code;
    private final Kind kind;

    public StoryDraftException(String code, String message, Kind kind) {
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
        FORBIDDEN,
        CONFLICT,
        NOT_FOUND,
        PRECONDITION
    }
}
