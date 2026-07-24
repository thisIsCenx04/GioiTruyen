package com.storyplatform.unit.identity.application;

import com.storyplatform.identity.application.MfaUseCase;
import com.storyplatform.identity.application.ReauthenticationScope;
import com.storyplatform.identity.application.ReauthenticationUseCase;
import com.storyplatform.identity.application.port.PasswordHasher;
import com.storyplatform.identity.application.port
        .ReauthenticationGrantRepository;
import com.storyplatform.identity.application.port
        .ReauthenticationTokenCodec;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.domain.GlobalRole;
import com.storyplatform.identity.domain.UserAccount;
import com.storyplatform.identity.domain.UserState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReauthenticationUseCaseTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final UserAccountRepository users =
            mock(UserAccountRepository.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final MfaUseCase mfa = mock(MfaUseCase.class);
    private final StubTokens tokens = new StubTokens();
    private final StubGrants grants = new StubGrants();
    private final ReauthenticationUseCase useCase = useCaseAt(NOW);

    @Test
    void issuesActorScopeAndTargetBoundShortLivedGrant() {
        validProof();

        var result = useCase.issue(command(
                ReauthenticationScope.TOPUP_MANUAL_APPROVAL,
                "topup",
                "topup-1"
        ));

        assertThat(result.status())
                .isEqualTo(ReauthenticationUseCase.Status.ISSUED);
        assertThat(result.token()).isEqualTo(tokens.raw);
        assertThat(result.expiresInSeconds()).isEqualTo(300);
        assertThat(grants.saved.actorId()).isEqualTo("user-1");
        assertThat(grants.saved.scope()).isEqualTo(
                ReauthenticationScope.TOPUP_MANUAL_APPROVAL
        );
        assertThat(grants.saved.targetType()).isEqualTo("topup");
        assertThat(grants.saved.targetId()).isEqualTo("topup-1");
        assertThat(grants.saved.tokenHash()).isEqualTo("token-hash");
        assertThat(grants.saved.expiresAt())
                .isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    void exactGrantConsumesOnceAndRejectsReplayOrScopeMismatch() {
        validProof();
        useCase.issue(command(
                ReauthenticationScope.WITHDRAWAL_APPROVAL,
                "withdrawal",
                "withdrawal-1"
        ));

        assertThat(useCase.consume(
                tokens.raw,
                "user-1",
                ReauthenticationScope.TOPUP_MANUAL_APPROVAL,
                "withdrawal",
                "withdrawal-1"
        )).isFalse();
        assertThat(useCase.consume(
                tokens.raw,
                "user-1",
                ReauthenticationScope.WITHDRAWAL_APPROVAL,
                "withdrawal",
                "withdrawal-1"
        )).isTrue();
        assertThat(useCase.consume(
                tokens.raw,
                "user-1",
                ReauthenticationScope.WITHDRAWAL_APPROVAL,
                "withdrawal",
                "withdrawal-1"
        )).isFalse();
    }

    @Test
    void expiredAndMalformedGrantsFailClosed() {
        validProof();
        useCase.issue(command(
                ReauthenticationScope.SYSTEM_CONFIG_CHANGE,
                "config",
                "discount-v1"
        ));

        assertThat(useCaseAt(NOW.plusSeconds(301)).consume(
                tokens.raw,
                "user-1",
                ReauthenticationScope.SYSTEM_CONFIG_CHANGE,
                "config",
                "discount-v1"
        )).isFalse();
        assertThat(useCase.consume(
                "malformed",
                "user-1",
                ReauthenticationScope.SYSTEM_CONFIG_CHANGE,
                "config",
                "discount-v1"
        )).isFalse();
    }

    @Test
    void publishedVerifierContractMapsAllowlistedScopeAndFailsClosed() {
        validProof();
        useCase.issue(command(
                ReauthenticationScope.SYSTEM_CONFIG_CHANGE,
                "configuration",
                "topup-discount"
        ));

        assertThat(useCase.consume(
                tokens.raw,
                "user-1",
                "SYSTEM_CONFIG_CHANGE",
                "configuration",
                "topup-discount"
        )).isTrue();
        assertThat(useCase.consume(
                tokens.raw,
                "user-1",
                "UNKNOWN_SCOPE",
                "configuration",
                "topup-discount"
        )).isFalse();
    }

    @Test
    void invalidPasswordMfaAndTargetNeverIssueGrant() {
        when(users.findById("user-1"))
                .thenReturn(Optional.of(account()));
        when(passwords.matches("password", "$argon2id$hash"))
                .thenReturn(false);

        assertThat(useCase.issue(command(
                ReauthenticationScope.EMAIL_CHANGE,
                "user",
                "user-1"
        )).status()).isEqualTo(
                ReauthenticationUseCase.Status.INVALID_PROOF
        );
        assertThat(useCase.issue(command(
                ReauthenticationScope.EMAIL_CHANGE,
                "../invalid",
                "user-1"
        )).status()).isEqualTo(
                ReauthenticationUseCase.Status.INVALID_TARGET
        );
        assertThat(grants.saved).isNull();
        verifyNoInteractions(mfa);
    }

    private void validProof() {
        UserAccount account = account();
        when(users.findById("user-1"))
                .thenReturn(Optional.of(account));
        when(passwords.matches("password", "$argon2id$hash"))
                .thenReturn(true);
        when(mfa.authenticate(account, "123456")).thenReturn(
                MfaUseCase.AuthenticationResult.VERIFIED
        );
    }

    private ReauthenticationUseCase useCaseAt(Instant instant) {
        return new ReauthenticationUseCase(
                users,
                passwords,
                mfa,
                tokens,
                grants,
                Duration.ofMinutes(5),
                Clock.fixed(instant, ZoneOffset.UTC),
                () -> UUID.fromString(
                        "0e2f7eac-e88d-4141-9430-9e685d71591c"
                )
        );
    }

    private static ReauthenticationUseCase.IssueCommand command(
            ReauthenticationScope scope,
            String targetType,
            String targetId
    ) {
        return new ReauthenticationUseCase.IssueCommand(
                "user-1",
                "password",
                "123456",
                scope,
                targetType,
                targetId
        );
    }

    private static UserAccount account() {
        return new UserAccount(
                "user-1",
                "admin@example.com",
                "$argon2id$hash",
                Set.of(GlobalRole.ADMIN),
                UserState.ACTIVE,
                1,
                "2026-07-24",
                NOW,
                NOW,
                NOW,
                0
        );
    }

    private static final class StubTokens
            implements ReauthenticationTokenCodec {

        private final String raw = "A".repeat(43);

        @Override
        public IssuedToken issue() {
            return new IssuedToken(raw, "token-hash");
        }

        @Override
        public String hash(String rawToken) {
            return raw.equals(rawToken) ? "token-hash" : "other";
        }

        @Override
        public boolean isWellFormed(String rawToken) {
            return raw.equals(rawToken);
        }
    }

    private static final class StubGrants
            implements ReauthenticationGrantRepository {

        private Grant saved;

        @Override
        public void save(Grant grant) {
            saved = grant;
        }

        @Override
        public boolean consume(
                String tokenHash,
                String actorId,
                ReauthenticationScope scope,
                String targetType,
                String targetId,
                Instant consumedAt
        ) {
            if (saved == null
                    || saved.consumedAt() != null
                    || !saved.expiresAt().isAfter(consumedAt)
                    || !saved.tokenHash().equals(tokenHash)
                    || !saved.actorId().equals(actorId)
                    || saved.scope() != scope
                    || !saved.targetType().equals(targetType)
                    || !saved.targetId().equals(targetId)) {
                return false;
            }
            saved = new Grant(
                    saved.id(),
                    saved.tokenHash(),
                    saved.actorId(),
                    saved.scope(),
                    saved.targetType(),
                    saved.targetId(),
                    saved.expiresAt(),
                    consumedAt,
                    saved.createdAt()
            );
            return true;
        }
    }
}
