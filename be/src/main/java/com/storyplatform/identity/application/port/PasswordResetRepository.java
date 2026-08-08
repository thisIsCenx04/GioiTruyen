package com.storyplatform.identity.application.port;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetRepository {

    void save(PasswordReset reset);

    Optional<PasswordReset> consume(String tokenHash, Instant consumedAt);

    record PasswordReset(
            String id,
            String userId,
            String tokenHash,
            Instant expiresAt,
            Instant consumedAt,
            Instant createdAt
    ) {
    }
}
