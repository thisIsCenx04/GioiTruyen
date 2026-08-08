package com.storyplatform.shared.cache;

/**
 * Isolates Redis workloads so keys and failure policies cannot be mixed.
 */
public enum RedisNamespace {
    CACHE("cache", RedisFailureMode.FALL_BACK_TO_SOURCE),
    RATE_LIMIT("rate", RedisFailureMode.FAIL_CLOSED),
    SESSION("session", RedisFailureMode.FAIL_CLOSED),
    COORDINATION("coord", RedisFailureMode.FAIL_CLOSED);

    private final String keySegment;
    private final RedisFailureMode failureMode;

    RedisNamespace(
            String keySegment,
            RedisFailureMode failureMode
    ) {
        this.keySegment = keySegment;
        this.failureMode = failureMode;
    }

    public String keySegment() {
        return keySegment;
    }

    public RedisFailureMode failureMode() {
        return failureMode;
    }
}
