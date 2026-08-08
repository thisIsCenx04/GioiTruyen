package com.storyplatform.community.application;

public interface CommentRateLimiter {
    boolean allow(String userId);
    long retryAfterSeconds();
}
