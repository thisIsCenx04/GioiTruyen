package com.storyplatform.unit.identity.infrastructure;

import com.storyplatform.identity.domain.GlobalRole;
import com.storyplatform.identity.domain.UserAccount;
import com.storyplatform.identity.domain.UserState;
import com.storyplatform.identity.infrastructure.AccessTokenProperties;
import com.storyplatform.identity.infrastructure.security
        .JwtAccessTokenIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAccessTokenIssuerTest {

    @Test
    void signsBoundedTokenWithSessionAndSecurityClaims() {
        byte[] keyBytes = new byte[32];
        SecretKeySpec key = new SecretKeySpec(
                keyBytes,
                "HmacSHA256"
        );
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        AccessTokenProperties properties = new AccessTokenProperties(
                "test-issuer",
                "test-audience",
                Duration.ofMinutes(10),
                "unused"
        );
        JwtAccessTokenIssuer issuer = new JwtAccessTokenIssuer(
                NimbusJwtEncoder.withSecretKey(key).build(),
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        var issued = issuer.issue(account(now), "session-1");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(Clock.fixed(
                now.plusSeconds(1),
                ZoneOffset.UTC
        ));
        decoder.setJwtValidator(timestamps);
        Jwt jwt = decoder.decode(issued.value());

        assertThat(issued.expiresInSeconds()).isEqualTo(600);
        assertThat(jwt.getSubject()).isEqualTo("user-1");
        assertThat(jwt.getAudience()).containsExactly("test-audience");
        assertThat(jwt.getClaimAsString("sid")).isEqualTo("session-1");
        assertThat(jwt.getClaimAsStringList("roles"))
                .containsExactly("USER");
        Object securityVersion = jwt.getClaim("security_version");
        assertThat(securityVersion).isEqualTo(4L);
        assertThat(jwt.getIssuedAt()).isEqualTo(now);
        assertThat(jwt.getExpiresAt())
                .isEqualTo(now.plus(Duration.ofMinutes(10)));
    }

    private static UserAccount account(Instant now) {
        return new UserAccount(
                "user-1",
                "reader@example.com",
                "$argon2id$hash",
                Set.of(GlobalRole.USER),
                UserState.ACTIVE,
                4,
                "2026-07-24",
                now,
                now,
                now,
                0
        );
    }
}
