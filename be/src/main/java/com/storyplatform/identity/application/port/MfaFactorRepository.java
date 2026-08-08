package com.storyplatform.identity.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MfaFactorRepository {

    Optional<Factor> findByUserId(String userId);

    boolean savePending(
            String userId,
            String protectedSecret,
            Instant createdAt
    );

    boolean activate(
            String userId,
            List<String> recoveryCodeHashes,
            Instant activatedAt
    );

    boolean consumeRecoveryCode(
            String userId,
            String recoveryCodeHash,
            Instant consumedAt
    );

    record Factor(
            String userId,
            String protectedSecret,
            boolean enabled
    ) {
    }
}
