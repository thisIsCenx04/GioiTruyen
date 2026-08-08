package com.storyplatform.media.application;

public final class MediaRequestException extends RuntimeException {

    private final String code;
    private final boolean forbidden;

    public MediaRequestException(
            String code,
            String message,
            boolean forbidden
    ) {
        super(message);
        this.code = code;
        this.forbidden = forbidden;
    }

    public String code() {
        return code;
    }

    public boolean forbidden() {
        return forbidden;
    }
}
