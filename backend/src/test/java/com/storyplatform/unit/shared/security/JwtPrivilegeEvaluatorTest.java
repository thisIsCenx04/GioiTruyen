package com.storyplatform.unit.shared.security;

import com.storyplatform.shared.security.JwtPrivilegeEvaluator;
import com.storyplatform.shared.security.PrivilegedCapability;
import com.storyplatform.shared.security.RoleCapabilityPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPrivilegeEvaluatorTest {

    private final JwtPrivilegeEvaluator evaluator =
            new JwtPrivilegeEvaluator(new RoleCapabilityPolicy());

    @Test
    void evaluatesOnlySignedTokenRoleListWithExactRoleNames() {
        assertThat(evaluator.allows(
                jwt(List.of("USER", "ADMIN")),
                PrivilegedCapability.FINANCE_REVIEW
        )).isTrue();
        assertThat(evaluator.allows(
                jwt(List.of("MODERATOR")),
                PrivilegedCapability.FINANCE_REVIEW
        )).isFalse();
        assertThat(evaluator.allows(
                jwt(List.of("MODERATOR")),
                PrivilegedCapability.MODERATION_DECIDE
        )).isTrue();
    }

    @Test
    void malformedMissingOrOversizedClaimsFailClosed() {
        assertThat(evaluator.allows(
                jwt("ADMIN"),
                PrivilegedCapability.SYSTEM_CONFIGURE
        )).isFalse();
        assertThat(evaluator.allows(
                jwt(null),
                PrivilegedCapability.SYSTEM_CONFIGURE
        )).isFalse();
        assertThat(evaluator.allows(
                jwt(List.of(
                        "ADMIN",
                        "r1",
                        "r2",
                        "r3",
                        "r4",
                        "r5",
                        "r6",
                        "r7",
                        "r8"
                )),
                PrivilegedCapability.SYSTEM_CONFIGURE
        )).isFalse();
    }

    private static Jwt jwt(Object roles) {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("user-1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
        if (roles != null) {
            builder.claim("roles", roles);
        }
        return builder.build();
    }
}
