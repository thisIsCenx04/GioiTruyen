package com.storyplatform.unit.identity.infrastructure;

import com.storyplatform.identity.application.port
        .RefreshTokenFamilyRepository;
import com.storyplatform.identity.infrastructure.security
        .SessionJwtValidator;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionJwtValidatorTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final RefreshTokenFamilyRepository families =
            mock(RefreshTokenFamilyRepository.class);
    private final SessionJwtValidator validator = new SessionJwtValidator(
            "story-web",
            families,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void acceptsOnlyExpectedAudienceAndActiveOwnedSession() {
        when(families.isActiveOwned(
                "session-1",
                "user-1",
                NOW
        )).thenReturn(true);

        assertThat(validator.validate(token(
                "user-1",
                "session-1",
                "story-web"
        )).hasErrors()).isFalse();
        assertThat(validator.validate(token(
                "user-1",
                "session-1",
                "other-app"
        )).hasErrors()).isTrue();
        assertThat(validator.validate(token(
                "user-1",
                "revoked",
                "story-web"
        )).hasErrors()).isTrue();
    }

    @Test
    void rejectsMissingSubjectOrSessionClaim() {
        Jwt missingSession = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("user-1")
                .audience(java.util.List.of("story-web"))
                .build();
        Jwt missingSubject = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .audience(java.util.List.of("story-web"))
                .claim("sid", "session-1")
                .build();

        assertThat(validator.validate(missingSession).hasErrors()).isTrue();
        assertThat(validator.validate(missingSubject).hasErrors()).isTrue();
    }

    private static Jwt token(
            String subject,
            String sessionId,
            String audience
    ) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .audience(java.util.List.of(audience))
                .claim("sid", sessionId)
                .build();
    }
}
