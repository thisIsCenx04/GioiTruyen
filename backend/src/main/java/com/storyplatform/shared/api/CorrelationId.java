package com.storyplatform.shared.api;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class CorrelationId {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String REQUEST_ATTRIBUTE = CorrelationId.class.getName();
    public static final String MDC_KEY = "correlationId";

    private static final Pattern VALID_VALUE =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private CorrelationId() {
    }

    public static String resolve(String candidate) {
        return resolve(candidate, () -> UUID.randomUUID().toString());
    }

    static String resolve(String candidate, Supplier<String> generator) {
        if (candidate != null && VALID_VALUE.matcher(candidate).matches()) {
            return candidate;
        }

        return Objects.requireNonNull(generator.get(), "generated correlation ID");
    }

    public static String from(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        return value instanceof String correlationId
                ? correlationId
                : resolve(request.getHeader(HEADER_NAME));
    }
}
