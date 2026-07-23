package com.storyplatform.identity.infrastructure.security;

import com.storyplatform.identity.application.port
        .RefreshTokenFamilyRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.util.Objects;

public final class SessionJwtValidator
        implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_SESSION = new OAuth2Error(
            "invalid_token",
            "The token session is invalid or revoked.",
            null
    );

    private final String audience;
    private final RefreshTokenFamilyRepository families;
    private final Clock clock;

    public SessionJwtValidator(
            String audience,
            RefreshTokenFamilyRepository families,
            Clock clock
    ) {
        this.audience = Objects.requireNonNull(audience, "audience");
        this.families = Objects.requireNonNull(families, "families");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String sessionId = token.getClaimAsString("sid");
        String userId = token.getSubject();
        if (!token.getAudience().contains(audience)
                || sessionId == null
                || userId == null
                || !families.isActiveOwned(
                        sessionId,
                        userId,
                        clock.instant()
                )) {
            return OAuth2TokenValidatorResult.failure(INVALID_SESSION);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
