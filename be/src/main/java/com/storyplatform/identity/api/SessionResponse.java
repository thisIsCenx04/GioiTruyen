package com.storyplatform.identity.api;

import com.storyplatform.identity.application.SessionView;

import java.time.Instant;

public record SessionResponse(
        String sessionId,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        boolean current
) {
    static SessionResponse from(SessionView view) {
        return new SessionResponse(
                view.sessionId(),
                view.createdAt(),
                view.lastUsedAt(),
                view.expiresAt(),
                view.current()
        );
    }
}
