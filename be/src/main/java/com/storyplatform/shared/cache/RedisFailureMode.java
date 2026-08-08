package com.storyplatform.shared.cache;

/**
 * Defines how a caller must behave when Redis cannot serve a request.
 */
public enum RedisFailureMode {
    FALL_BACK_TO_SOURCE,
    FAIL_CLOSED
}
