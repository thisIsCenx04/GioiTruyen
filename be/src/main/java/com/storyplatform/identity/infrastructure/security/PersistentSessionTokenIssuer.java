package com.storyplatform.identity.infrastructure.security;

import com.storyplatform.identity.application.port.AccessTokenIssuer;
import com.storyplatform.identity.application.port.RefreshTokenCodec;
import com.storyplatform.identity.application.port
        .RefreshTokenFamilyRepository;
import com.storyplatform.identity.application.port.SessionTokenIssuer;
import com.storyplatform.identity.domain.RefreshTokenFamily;
import com.storyplatform.identity.domain.UserAccount;
import com.storyplatform.identity.infrastructure.RefreshSessionProperties;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class PersistentSessionTokenIssuer
        implements SessionTokenIssuer {

    private final AccessTokenIssuer accessTokens;
    private final RefreshTokenCodec refreshTokens;
    private final RefreshTokenFamilyRepository families;
    private final RefreshSessionProperties properties;
    private final Supplier<UUID> idGenerator;
    private final Clock clock;

    public PersistentSessionTokenIssuer(
            AccessTokenIssuer accessTokens,
            RefreshTokenCodec refreshTokens,
            RefreshTokenFamilyRepository families,
            RefreshSessionProperties properties,
            Supplier<UUID> idGenerator,
            Clock clock
    ) {
        this.accessTokens = Objects.requireNonNull(
                accessTokens,
                "accessTokens"
        );
        this.refreshTokens = Objects.requireNonNull(
                refreshTokens,
                "refreshTokens"
        );
        this.families = Objects.requireNonNull(families, "families");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "idGenerator"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public IssuedSession issue(UserAccount account) {
        Instant now = clock.instant();
        RefreshTokenCodec.IssuedRefreshToken refresh =
                refreshTokens.issue();
        String familyId = idGenerator.get().toString();
        families.create(RefreshTokenFamily.active(
                familyId,
                account.id(),
                account.securityVersion(),
                refresh.hash(),
                now.plus(properties.ttl()),
                now
        ));
        AccessTokenIssuer.IssuedAccessToken access =
                accessTokens.issue(account, familyId);
        return new IssuedSession(
                access.value(),
                access.expiresInSeconds(),
                refresh.value()
        );
    }
}
