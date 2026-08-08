package com.storyplatform.unit.identity.domain;

import com.storyplatform.identity.domain.RefreshTokenFamily;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RefreshTokenFamilyTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void activeFactoryCreatesBoundedUnrevokedFamily() {
        RefreshTokenFamily family = RefreshTokenFamily.active(
                "family-1",
                "user-1",
                2,
                "token-hash",
                NOW.plusSeconds(3600),
                NOW
        );

        assertThat(family.usedTokenHashes()).isEmpty();
        assertThat(family.generation()).isZero();
        assertThat(family.securityVersion()).isEqualTo(2);
        assertThat(family.revokedAt()).isNull();
        assertThat(family.revokeReason()).isNull();
    }

    @Test
    void rejectsInvalidIdentityVersionAndTimestamps() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                family(" ", 1, 0, NOW.plusSeconds(1), null, null)
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
                family("family-1", 0, 0, NOW.plusSeconds(1), null, null)
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
                family("family-1", 1, -1, NOW.plusSeconds(1), null, null)
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
                family("family-1", 1, 0, NOW, null, null)
        );
    }

    @Test
    void requiresCompleteRevocationMetadata() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                family(
                        "family-1",
                        1,
                        1,
                        NOW.plusSeconds(1),
                        NOW,
                        null
                )
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
                family(
                        "family-1",
                        1,
                        1,
                        NOW.plusSeconds(1),
                        null,
                        "REUSED"
                )
        );
    }

    private static RefreshTokenFamily family(
            String id,
            long securityVersion,
            int generation,
            Instant expiresAt,
            Instant revokedAt,
            String reason
    ) {
        return new RefreshTokenFamily(
                id,
                "user-1",
                securityVersion,
                "token-hash",
                List.of(),
                generation,
                expiresAt,
                revokedAt,
                reason,
                NOW,
                NOW
        );
    }
}
