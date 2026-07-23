package com.storyplatform.unit.identity.application;

import com.storyplatform.identity.application.RefreshSessionOutcome;
import com.storyplatform.identity.application.RefreshSessionUseCase;
import com.storyplatform.identity.application.port.AccessTokenIssuer;
import com.storyplatform.identity.application.port.RefreshTokenCodec;
import com.storyplatform.identity.application.port
        .RefreshTokenFamilyRepository;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.domain.GlobalRole;
import com.storyplatform.identity.domain.UserAccount;
import com.storyplatform.identity.domain.UserState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshSessionUseCaseTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final StubCodec tokens = new StubCodec();
    private final StubFamilies families = new StubFamilies();
    private final StubUsers users = new StubUsers();
    private final RefreshSessionUseCase useCase =
            new RefreshSessionUseCase(
                    tokens,
                    families,
                    users,
                    account -> new AccessTokenIssuer.IssuedAccessToken(
                            "new.access.token",
                            600
                    ),
                    500,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    void rotatesCurrentTokenAndIssuesNewPair() {
        families.result = RefreshTokenFamilyRepository.RotationResult
                .rotated("family-1", "user-1", 3);
        users.account = Optional.of(account(UserState.ACTIVE, 3));

        RefreshSessionOutcome outcome = useCase.refresh("valid-token");

        assertThat(outcome.status())
                .isEqualTo(RefreshSessionOutcome.Status.ROTATED);
        assertThat(outcome.accessToken()).isEqualTo("new.access.token");
        assertThat(outcome.refreshToken()).isEqualTo("next-token");
        assertThat(families.presentedHash).isEqualTo("presented-hash");
        assertThat(families.nextHash).isEqualTo("next-hash");
    }

    @Test
    void rejectsMalformedUnknownAndReplayedTokensGenerically() {
        assertThat(useCase.refresh("bad").status())
                .isEqualTo(RefreshSessionOutcome.Status.INVALID);
        assertThat(families.rotations).isZero();

        families.result =
                RefreshTokenFamilyRepository.RotationResult.invalid();
        assertThat(useCase.refresh("valid-token").status())
                .isEqualTo(RefreshSessionOutcome.Status.INVALID);

        families.result = RefreshTokenFamilyRepository.RotationResult
                .reuseDetected("family-1");
        assertThat(useCase.refresh("valid-token").status())
                .isEqualTo(
                        RefreshSessionOutcome.Status.REUSE_DETECTED
                );
    }

    @Test
    void revokesRotatedFamilyWhenAccountSecurityChanged() {
        families.result = RefreshTokenFamilyRepository.RotationResult
                .rotated("family-1", "user-1", 2);
        users.account = Optional.of(account(UserState.ACTIVE, 3));

        assertThat(useCase.refresh("valid-token").status())
                .isEqualTo(RefreshSessionOutcome.Status.INVALID);
        assertThat(families.revokedFamily).isEqualTo("family-1");
        assertThat(families.revokeReason).isEqualTo(
                RefreshSessionUseCase.ACCOUNT_INVALID_REASON
        );
    }

    private static UserAccount account(
            UserState state,
            long securityVersion
    ) {
        return new UserAccount(
                "user-1",
                "reader@example.com",
                "$argon2id$hash",
                Set.of(GlobalRole.USER),
                state,
                securityVersion,
                "2026-07-24",
                NOW,
                NOW,
                NOW,
                0
        );
    }

    private static final class StubCodec implements RefreshTokenCodec {

        @Override
        public IssuedRefreshToken issue() {
            return new IssuedRefreshToken("next-token", "next-hash");
        }

        @Override
        public String hash(String rawToken) {
            return "presented-hash";
        }

        @Override
        public boolean isWellFormed(String rawToken) {
            return "valid-token".equals(rawToken);
        }
    }

    private static final class StubFamilies
            implements RefreshTokenFamilyRepository {

        private RotationResult result = RotationResult.invalid();
        private int rotations;
        private String presentedHash;
        private String nextHash;
        private String revokedFamily;
        private String revokeReason;

        @Override
        public void create(
                com.storyplatform.identity.domain.RefreshTokenFamily family
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RotationResult rotate(
                String currentTokenHash,
                String nextTokenHash,
                Instant now,
                int maximumGeneration
        ) {
            rotations++;
            presentedHash = currentTokenHash;
            nextHash = nextTokenHash;
            return result;
        }

        @Override
        public void revoke(
                String familyId,
                Instant revokedAt,
                String reason
        ) {
            revokedFamily = familyId;
            revokeReason = reason;
        }
    }

    private static final class StubUsers
            implements UserAccountRepository {

        private Optional<UserAccount> account = Optional.empty();

        @Override
        public boolean saveIfEmailAvailable(UserAccount account) {
            return false;
        }

        @Override
        public boolean activatePending(
                String userId,
                Instant activatedAt
        ) {
            return false;
        }

        @Override
        public Optional<UserAccount> findById(String userId) {
            return account;
        }
    }
}
