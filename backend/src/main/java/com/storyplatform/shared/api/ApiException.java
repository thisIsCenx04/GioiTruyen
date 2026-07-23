package com.storyplatform.shared.api;

import org.springframework.http.HttpStatus;

import java.util.Objects;

public final class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String title;

    public ApiException(
            HttpStatus status,
            String code,
            String title,
            String detail
    ) {
        super(detail);
        this.status = Objects.requireNonNull(status, "status");
        this.code = requireText(code, "code");
        this.title = requireText(title, "title");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
