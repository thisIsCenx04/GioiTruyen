package com.storyplatform.publishing.application;

public final class PublishingDueException extends RuntimeException {

    private final String code;

    public PublishingDueException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
