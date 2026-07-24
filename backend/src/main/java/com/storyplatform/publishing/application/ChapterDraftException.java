package com.storyplatform.publishing.application;

public final class ChapterDraftException extends RuntimeException {

    private final String code;
    private final Kind kind;

    public ChapterDraftException(String code, String message, Kind kind) {
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
        NOT_FOUND,
        CONFLICT
    }
}
