package com.storyplatform.identity.application;

import java.time.Instant;

public record SessionView(
        String sessionId,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        boolean current
) {
}
