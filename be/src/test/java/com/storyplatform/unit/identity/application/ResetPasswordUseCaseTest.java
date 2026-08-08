package com.storyplatform.unit.identity.application;

import com.storyplatform.identity.application.PasswordResetOutcome;
import com.storyplatform.identity.application.ResetPasswordUseCase;
import com.storyplatform.identity.application.port.PasswordHasher;
import com.storyplatform.identity.application.port.PasswordResetRepository;
import com.storyplatform.identity.application.port.RefreshTokenFamilyRepository;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.application.port.VerificationTokenCodec;
import com.storyplatform.identity.domain.PasswordPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResetPasswordUseCaseTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final PasswordResetRepository resets =
            mock(PasswordResetRepository.class);
    private final UserAccountRepository users =
            mock(UserAccountRepository.class);
    private final RefreshTokenFamilyRepository sessions =
            mock(RefreshTokenFamilyRepository.class);
    private final VerificationTokenCodec tokens =
            mock(VerificationTokenCodec.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final ResetPasswordUseCase useCase = new ResetPasswordUseCase(
            resets,
            users,
            sessions,
            tokens,
            passwords,
            new PasswordPolicy(),
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void consumesOnceChangesPasswordAndRevokesSessions() {
        String rawToken = "valid-token";
        var reset = new PasswordResetRepository.PasswordReset(
                "reset-1",
                "user-1",
                "hash",
                NOW.plusSeconds(60),
                NOW,
                NOW.minusSeconds(1)
        );
        when(tokens.isWellFormed(rawToken)).thenReturn(true);
        when(tokens.hash(rawToken)).thenReturn("hash");
        when(resets.consume("hash", NOW)).thenReturn(Optional.of(reset));
        when(passwords.hash("new secure password")).thenReturn("argon");
        when(users.resetPassword("user-1", "argon", NOW))
                .thenReturn(true);

        assertThat(useCase.reset(rawToken, "new secure password"))
                .isEqualTo(PasswordResetOutcome.RESET);
        verify(sessions).revokeAllOwned(
                "user-1",
                NOW,
                "USER_REVOKED"
        );
    }

    @Test
    void replayAndMalformedTokensShareGenericOutcome() {
        when(tokens.isWellFormed("replay")).thenReturn(true);
        when(tokens.hash("replay")).thenReturn("used");
        when(resets.consume("used", NOW)).thenReturn(Optional.empty());

        assertThat(useCase.reset("replay", "new secure password"))
                .isEqualTo(PasswordResetOutcome.INVALID_OR_EXPIRED);
        assertThat(useCase.reset("malformed", "new secure password"))
                .isEqualTo(PasswordResetOutcome.INVALID_OR_EXPIRED);
        verifyNoInteractions(users, sessions);
    }

    @Test
    void weakPasswordDoesNotConsumeToken() {
        assertThat(useCase.reset("token", "short"))
                .isEqualTo(PasswordResetOutcome.WEAK_PASSWORD);
        verifyNoInteractions(resets, users, sessions, tokens, passwords);
    }
}
