package com.storyplatform.unit.reading.api;

import com.storyplatform.reading.api.ReadingSessionController;
import com.storyplatform.reading.api.StartReadingSessionRequest;
import com.storyplatform.reading.application.ReadingSessionException;
import com.storyplatform.reading.application.ReadingSessionOperations;
import com.storyplatform.shared.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadingSessionControllerTest {

    @Test
    void startsAnonymousAndAuthenticatedSessionsWithNoStore() {
        var operations = mock(ReadingSessionOperations.class);
        var servlet = mock(HttpServletRequest.class);
        when(servlet.getRemoteAddr()).thenReturn("10.0.0.1");
        Instant expiresAt = Instant.parse("2026-07-24T00:30:00Z");
        when(operations.start(any())).thenReturn(
                new ReadingSessionOperations.SessionGrant(
                        "40000000-0000-4000-8000-000000000001",
                        "signed",
                        expiresAt,
                        15
                )
        );
        var controller = new ReadingSessionController(operations);
        var request = new StartReadingSessionRequest(
                "20000000-0000-4000-8000-000000000001",
                "30000000-0000-4000-8000-000000000001",
                "10000000-0000-4000-8000-000000000002"
        );

        var anonymous = controller.start(null, request, servlet);
        var authenticated = controller.start(jwt(), request, servlet);

        assertThat(anonymous.getStatusCode().value()).isEqualTo(201);
        assertThat(authenticated.getHeaders().getCacheControl())
                .contains("no-store");
        assertThat(authenticated.getHeaders().getLocation())
                .hasPath("/api/v1/reading-sessions/"
                        + "40000000-0000-4000-8000-000000000001");
    }

    @Test
    void mapsQuotaFailuresAndRetryAfter() {
        var operations = mock(ReadingSessionOperations.class);
        when(operations.start(any())).thenThrow(
                new ReadingSessionException(
                        "READING_SESSION_RATE_LIMITED",
                        "limited",
                        ReadingSessionException.Kind.RATE_LIMITED,
                        60
                )
        );
        var controller = new ReadingSessionController(operations);

        assertThatThrownBy(() -> controller.start(
                null,
                new StartReadingSessionRequest("story", "chapter", "anon"),
                mock(HttpServletRequest.class)
        )).isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    var api = (ApiException) error;
                    assertThat(api.status())
                            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(api.retryAfter()).hasSeconds(60);
                });
    }

    private static Jwt jwt() {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("10000000-0000-4000-8000-000000000001")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }
}
