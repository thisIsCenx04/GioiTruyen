package com.storyplatform.reading.application;

public final class ReadingProgressException extends RuntimeException {

    private final String code;
    private final Kind kind;

    public ReadingProgressException(
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
        NOT_FOUND,
        CONFLICT
    }
}
