package com.storyplatform.shared.observability;

import java.util.regex.Pattern;

/**
 * Bounds values before they become log fields, metric tags, or span attributes.
 */
public final class TelemetryValueSanitizer {

    private static final Pattern EVENT_TYPE = Pattern.compile(
            "[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*){2,}"
    );
    private static final int MAX_EVENT_TYPE_LENGTH = 128;

    private TelemetryValueSanitizer() {
    }

    public static String eventType(String candidate) {
        if (candidate == null
                || candidate.length() > MAX_EVENT_TYPE_LENGTH
                || !EVENT_TYPE.matcher(candidate).matches()) {
            return "unknown";
        }
        return candidate;
    }

    public static String eventVersion(int candidate) {
        return candidate >= 1 && candidate <= 999
                ? Integer.toString(candidate)
                : "unknown";
    }
}
