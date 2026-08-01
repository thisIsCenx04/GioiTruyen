package com.storyplatform.admin.application;

public final class AdminContentException extends RuntimeException {

    private final String code;

    public AdminContentException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
