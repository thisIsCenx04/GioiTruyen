package com.storyplatform.publishing.application;

public final class ContentVisibilityException extends RuntimeException {

    private final Kind kind;
    private final String code;

    public ContentVisibilityException(
            String code,
            String message,
            Kind kind
    ) {
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
        PRECONDITION,
        CONFLICT
    }
}
