package com.storyplatform.unit.identity.application;

import com.storyplatform.identity.application.SessionManagementUseCase;
import com.storyplatform.identity.application.SessionView;
import com.storyplatform.identity.application.port
        .RefreshTokenFamilyRepository;
import com.storyplatform.identity.domain.RefreshTokenFamily;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionManagementUseCaseTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final StubFamilies families = new StubFamilies();
    private final SessionManagementUseCase useCase =
            new SessionManagementUseCase(
                    families,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    void listsMinimalSessionsWithCurrentFirstByActivity() {
        families.sessions = List.of(
                new RefreshTokenFamilyRepository.SessionRecord(
                        "older",
                        NOW.minusSeconds(20),
                        NOW.minusSeconds(10),
                        NOW.plusSeconds(100)
                ),
                new RefreshTokenFamilyRepository.SessionRecord(
                        "current",
                        NOW.minusSeconds(10),
                        NOW,
                        NOW.plusSeconds(100)
                )
        );

        List<SessionView> result = useCase.list("user-1", "current");

        assertThat(result).extracting(SessionView::sessionId)
                .containsExactly("current", "older");
        assertThat(result.getFirst().current()).isTrue();
        assertThat(result.getLast().current()).isFalse();
    }

    @Test
    void revocationIsOwnedIdempotentAndUsesSafeReason() {
        useCase.revokeCurrent("user-1", "current");
        assertThat(families.revokedId).isEqualTo("current");
        assertThat(families.revokedUser).isEqualTo("user-1");
        assertThat(families.reason).isEqualTo(
                SessionManagementUseCase.USER_REVOKED_REASON
        );

        useCase.revokeSpecific("user-1", "remote");
        assertThat(families.revokedId).isEqualTo("remote");

        useCase.revokeAll("user-1");
        assertThat(families.revokeAllUser).isEqualTo("user-1");
    }

    private static final class StubFamilies
            implements RefreshTokenFamilyRepository {

        private List<SessionRecord> sessions = List.of();
        private String revokedId;
        private String revokedUser;
        private String revokeAllUser;
        private String reason;

        @Override
        public void create(RefreshTokenFamily family) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RotationResult rotate(
                String currentTokenHash,
                String nextTokenHash,
                Instant now,
                int maximumGeneration
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void revoke(
                String familyId,
                Instant revokedAt,
                String reason
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean revokeOwned(
                String familyId,
                String userId,
                Instant revokedAt,
                String revokeReason
        ) {
            revokedId = familyId;
            revokedUser = userId;
            reason = revokeReason;
            return true;
        }

        @Override
        public long revokeAllOwned(
                String userId,
                Instant revokedAt,
                String revokeReason
        ) {
            revokeAllUser = userId;
            reason = revokeReason;
            return 2;
        }

        @Override
        public List<SessionRecord> findActiveByUser(
                String userId,
                Instant now
        ) {
            return sessions;
        }

        @Override
        public boolean isActiveOwned(
                String familyId,
                String userId,
                Instant now
        ) {
            return false;
        }
    }
}
