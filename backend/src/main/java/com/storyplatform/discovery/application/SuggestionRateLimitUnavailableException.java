package com.storyplatform.discovery.application;

public final class SuggestionRateLimitUnavailableException
        extends RuntimeException {

    public SuggestionRateLimitUnavailableException(Throwable cause) {
        super("suggestion rate limit is unavailable", cause);
    }
}
