package com.storyplatform.identity.application.port;

import com.storyplatform.identity.domain.RefreshTokenFamily;

import java.time.Instant;
import java.util.List;

public interface RefreshTokenFamilyRepository {

    void create(RefreshTokenFamily family);

    RotationResult rotate(
            String currentTokenHash,
            String nextTokenHash,
            Instant now,
            int maximumGeneration
    );

    void revoke(String familyId, Instant revokedAt, String reason);

    boolean revokeOwned(
            String familyId,
            String userId,
            Instant revokedAt,
            String reason
    );

    long revokeAllOwned(
            String userId,
            Instant revokedAt,
            String reason
    );

    List<SessionRecord> findActiveByUser(String userId, Instant now);

    boolean isActiveOwned(
            String familyId,
            String userId,
            Instant now
    );

    enum RotationStatus {
        ROTATED,
        REUSE_DETECTED,
        INVALID
    }

    record RotationResult(
            RotationStatus status,
            String familyId,
            String userId,
            long securityVersion
    ) {
        public static RotationResult rotated(
                String familyId,
                String userId,
                long securityVersion
        ) {
            return new RotationResult(
                    RotationStatus.ROTATED,
                    familyId,
                    userId,
                    securityVersion
            );
        }

        public static RotationResult reuseDetected(String familyId) {
            return new RotationResult(
                    RotationStatus.REUSE_DETECTED,
                    familyId,
                    null,
                    0
            );
        }

        public static RotationResult invalid() {
            return new RotationResult(
                    RotationStatus.INVALID,
                    null,
                    null,
                    0
            );
        }
    }

    record SessionRecord(
            String id,
            Instant createdAt,
            Instant lastUsedAt,
            Instant expiresAt
    ) {
    }
}
