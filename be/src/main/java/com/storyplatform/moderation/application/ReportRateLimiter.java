package com.storyplatform.moderation.application;

public interface ReportRateLimiter {
    boolean allow(String reporterId, int trustScore);
    long retryAfterSeconds();
}
