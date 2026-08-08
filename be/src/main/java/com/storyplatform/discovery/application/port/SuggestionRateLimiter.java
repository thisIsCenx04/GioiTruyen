package com.storyplatform.discovery.application.port;

public interface SuggestionRateLimiter {

    boolean allow(String subject);

    long retryAfterSeconds();
}
