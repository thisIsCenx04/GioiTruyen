package com.storyplatform.identity.infrastructure.security;

import com.storyplatform.identity.application.port.AccessTokenIssuer;
import com.storyplatform.identity.domain.GlobalRole;
import com.storyplatform.identity.domain.UserAccount;
import com.storyplatform.identity.infrastructure.AccessTokenProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final AccessTokenProperties properties;
    private final Clock clock;

    public JwtAccessTokenIssuer(
            JwtEncoder encoder,
            AccessTokenProperties properties,
            Clock clock
    ) {
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public IssuedAccessToken issue(UserAccount account) {
        Objects.requireNonNull(account, "account");
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.ttl());
        List<String> roles = account.globalRoles().stream()
                .map(GlobalRole::name)
                .sorted()
                .toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(account.id())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .claim("security_version", account.securityVersion())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
        return new IssuedAccessToken(
                value,
                properties.ttl().toSeconds()
        );
    }
}
