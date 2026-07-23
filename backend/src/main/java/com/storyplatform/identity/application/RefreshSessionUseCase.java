package com.storyplatform.identity.application;

import com.storyplatform.identity.application.port.AccessTokenIssuer;
import com.storyplatform.identity.application.port.RefreshTokenCodec;
import com.storyplatform.identity.application.port
        .RefreshTokenFamilyRepository;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.domain.UserAccount;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class RefreshSessionUseCase {

    public static final String ACCOUNT_INVALID_REASON =
            "ACCOUNT_SECURITY_CHANGED";

    private final RefreshTokenCodec tokens;
    private final RefreshTokenFamilyRepository families;
    private final UserAccountRepository users;
    private final AccessTokenIssuer accessTokens;
    private final int maximumGenerations;
    private final Clock clock;

    public RefreshSessionUseCase(
            RefreshTokenCodec tokens,
            RefreshTokenFamilyRepository families,
            UserAccountRepository users,
            AccessTokenIssuer accessTokens,
            int maximumGenerations,
            Clock clock
    ) {
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.families = Objects.requireNonNull(families, "families");
        this.users = Objects.requireNonNull(users, "users");
        this.accessTokens = Objects.requireNonNull(
                accessTokens,
                "accessTokens"
        );
        if (maximumGenerations < 1) {
            throw new IllegalArgumentException(
                    "maximumGenerations must be positive"
            );
        }
        this.maximumGenerations = maximumGenerations;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RefreshSessionOutcome refresh(String rawToken) {
        if (!tokens.isWellFormed(rawToken)) {
            return RefreshSessionOutcome.invalid();
        }

        RefreshTokenCodec.IssuedRefreshToken next = tokens.issue();
        Instant now = clock.instant();
        RefreshTokenFamilyRepository.RotationResult rotation =
                families.rotate(
                        tokens.hash(rawToken),
                        next.hash(),
                        now,
                        maximumGenerations
                );
        return switch (rotation.status()) {
            case INVALID -> RefreshSessionOutcome.invalid();
            case REUSE_DETECTED ->
                    RefreshSessionOutcome.reuseDetected();
            case ROTATED -> completeRotation(rotation, next, now);
        };
    }

    private RefreshSessionOutcome completeRotation(
            RefreshTokenFamilyRepository.RotationResult rotation,
            RefreshTokenCodec.IssuedRefreshToken next,
            Instant now
    ) {
        Optional<UserAccount> account = users.findById(rotation.userId());
        if (account.isEmpty()
                || !account.orElseThrow().isActive()
                || account.orElseThrow().securityVersion()
                != rotation.securityVersion()) {
            families.revoke(
                    rotation.familyId(),
                    now,
                    ACCOUNT_INVALID_REASON
            );
            return RefreshSessionOutcome.invalid();
        }
        AccessTokenIssuer.IssuedAccessToken access =
                accessTokens.issue(
                        account.orElseThrow(),
                        rotation.familyId()
                );
        return RefreshSessionOutcome.rotated(
                access.value(),
                access.expiresInSeconds(),
                next.value()
        );
    }
}
