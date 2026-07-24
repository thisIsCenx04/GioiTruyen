package com.storyplatform.discovery.application;

public final class SuggestionRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public SuggestionRateLimitException(long retryAfterSeconds) {
        super("suggestion request limit exceeded");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
