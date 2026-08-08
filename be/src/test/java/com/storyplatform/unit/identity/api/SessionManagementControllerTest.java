package com.storyplatform.unit.identity.api;

import com.storyplatform.identity.api.SessionManagementController;
import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.identity.application.SessionView;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionManagementControllerTest {

    private static final String SESSION_ID =
            "21a64aa0-c960-4a90-a97c-b953fd1eeb3c";
    private IdentityService identityService;
    private SessionManagementController controller;
    private Jwt jwt;

    @BeforeEach
    void configure() {
        identityService = mock(IdentityService.class);
        controller = new SessionManagementController(identityService);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("user-1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim("sid", SESSION_ID)
                .build();
    }

    @Test
    void listsMinimalSessionMetadataAndCurrentMarker() {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        when(identityService.listSessions("user-1", SESSION_ID))
                .thenReturn(List.of(new SessionView(
                        SESSION_ID,
                        now.minusSeconds(10),
                        now,
                        now.plusSeconds(100),
                        true
                )));

        var response = controller.list(jwt);

        assertThat(response.sessions()).singleElement().satisfies(session -> {
            assertThat(session.sessionId()).isEqualTo(SESSION_ID);
            assertThat(session.current()).isTrue();
            assertThat(session.lastUsedAt()).isEqualTo(now);
        });
    }

    @Test
    void logoutSpecificAndAllRevocationsAreNoContent() {
        assertThat(controller.logout(jwt).getStatusCode().value())
                .isEqualTo(204);
        verify(identityService).logout("user-1", SESSION_ID);

        assertThat(controller.revoke(jwt, SESSION_ID)
                .getStatusCode().value()).isEqualTo(204);
        verify(identityService).revokeSession("user-1", SESSION_ID);

        assertThat(controller.revokeAll(jwt).getStatusCode().value())
                .isEqualTo(204);
        verify(identityService).revokeAllSessions("user-1");
    }

    @Test
    void malformedRemoteSessionIdIsRejected() {
        assertThatThrownBy(() -> controller.revoke(jwt, "invalid"))
                .isInstanceOf(ApiException.class)
                .extracting(exception ->
                        ((ApiException) exception).code())
                .isEqualTo("SESSION_ID_INVALID");
    }
}
