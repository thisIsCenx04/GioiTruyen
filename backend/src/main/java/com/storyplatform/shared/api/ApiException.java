package com.storyplatform.shared.api;

import org.springframework.http.HttpStatus;

import java.util.Objects;
import java.time.Duration;

public final class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String title;
    private final Duration retryAfter;

    public ApiException(
            HttpStatus status,
            String code,
            String title,
            String detail
    ) {
        this(status, code, title, detail, null);
    }

    public ApiException(
            HttpStatus status,
            String code,
            String title,
            String detail,
            Duration retryAfter
    ) {
        super(detail);
        this.status = Objects.requireNonNull(status, "status");
        this.code = requireText(code, "code");
        this.title = requireText(title, "title");
        if (retryAfter != null
                && (retryAfter.isZero()
                || retryAfter.isNegative()
                || retryAfter.compareTo(Duration.ofDays(1)) > 0)) {
            throw new IllegalArgumentException(
                    "retryAfter must be positive and at most one day"
            );
        }
        this.retryAfter = retryAfter;
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

    public Duration retryAfter() {
        return retryAfter;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
