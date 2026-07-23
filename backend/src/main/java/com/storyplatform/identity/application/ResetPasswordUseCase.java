package com.storyplatform.identity.application;

import com.storyplatform.identity.application.port.PasswordHasher;
import com.storyplatform.identity.application.port.PasswordResetRepository;
import com.storyplatform.identity.application.port.RefreshTokenFamilyRepository;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.application.port.VerificationTokenCodec;
import com.storyplatform.identity.domain.PasswordPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class ResetPasswordUseCase {

    private final PasswordResetRepository resets;
    private final UserAccountRepository users;
    private final RefreshTokenFamilyRepository sessions;
    private final VerificationTokenCodec tokens;
    private final PasswordHasher passwords;
    private final PasswordPolicy policy;
    private final Clock clock;

    public ResetPasswordUseCase(
            PasswordResetRepository resets,
            UserAccountRepository users,
            RefreshTokenFamilyRepository sessions,
            VerificationTokenCodec tokens,
            PasswordHasher passwords,
            PasswordPolicy policy,
            Clock clock
    ) {
        this.resets = Objects.requireNonNull(resets, "resets");
        this.users = Objects.requireNonNull(users, "users");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PasswordResetOutcome reset(
            String rawToken,
            String newPassword
    ) {
        if (!policy.accepts(newPassword)) {
            return PasswordResetOutcome.WEAK_PASSWORD;
        }
        if (!tokens.isWellFormed(rawToken)) {
            return PasswordResetOutcome.INVALID_OR_EXPIRED;
        }
        Instant now = clock.instant();
        return resets.consume(tokens.hash(rawToken), now)
                .map(reset -> apply(reset.userId(), newPassword, now))
                .orElse(PasswordResetOutcome.INVALID_OR_EXPIRED);
    }

    private PasswordResetOutcome apply(
            String userId,
            String newPassword,
            Instant now
    ) {
        if (!users.resetPassword(
                userId,
                passwords.hash(newPassword),
                now
        )) {
            throw new IllegalStateException(
                    "Reset consumed for an inactive account"
            );
        }
        sessions.revokeAllOwned(
                userId,
                now,
                SessionManagementUseCase.USER_REVOKED_REASON
        );
        return PasswordResetOutcome.RESET;
    }
}
