package com.storyplatform.media.application;

public final class MediaPolicyException extends RuntimeException {

    private final String code;

    public MediaPolicyException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
