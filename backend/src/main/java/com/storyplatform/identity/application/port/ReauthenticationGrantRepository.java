package com.storyplatform.identity.application.port;

import com.storyplatform.identity.application.ReauthenticationScope;

import java.time.Instant;

public interface ReauthenticationGrantRepository {

    void save(Grant grant);

    boolean consume(
            String tokenHash,
            String actorId,
            ReauthenticationScope scope,
            String targetType,
            String targetId,
            Instant consumedAt
    );

    record Grant(
            String id,
            String tokenHash,
            String actorId,
            ReauthenticationScope scope,
            String targetType,
            String targetId,
            Instant expiresAt,
            Instant consumedAt,
            Instant createdAt
    ) {
    }
}
