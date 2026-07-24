package com.storyplatform.media.application;

public final class MediaGatewayException extends RuntimeException {

    private final String code;

    public MediaGatewayException(
            String code,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
