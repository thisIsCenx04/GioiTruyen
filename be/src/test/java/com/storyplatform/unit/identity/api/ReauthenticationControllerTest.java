package com.storyplatform.unit.identity.api;

import com.storyplatform.identity.api.ReauthenticationController;
import com.storyplatform.identity.api.ReauthenticationGrantRequest;
import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.identity.application.LoginRiskUnavailableException;
import com.storyplatform.identity.application.ReauthenticationScope;
import com.storyplatform.identity.application.ReauthenticationUseCase;
import com.storyplatform.identity.application.port.LoginRiskLimiter;
import com.storyplatform.shared.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReauthenticationControllerTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final IdentityService service = mock(IdentityService.class);
    private final LoginRiskLimiter risk = mock(LoginRiskLimiter.class);
    private final HttpServletRequest servletRequest =
            mock(HttpServletRequest.class);
    private final ReauthenticationController controller =
            new ReauthenticationController(service, risk);
    private final Jwt jwt = new Jwt(
            "token",
            NOW,
            NOW.plusSeconds(600),
            Map.of("alg", "HS256"),
            Map.of("sub", "user-1")
    );

    @Test
    void returnsShortLivedScopedGrant() {
        allow();
        when(service.issueReauthenticationGrant(any())).thenReturn(
                new ReauthenticationUseCase.IssueResult(
                        ReauthenticationUseCase.Status.ISSUED,
                        "grant-token",
                        300,
                        NOW.plusSeconds(300)
                )
        );

        var response = controller.issue(
                jwt,
                request(),
                servletRequest
        );

        assertThat(response.grantToken()).isEqualTo("grant-token");
        assertThat(response.grantType())
                .isEqualTo("Scoped-Reauthentication");
        assertThat(response.expiresIn()).isEqualTo(300);
        verify(risk).recordSuccess("reauth:user-1", null);
    }

    @Test
    void invalidProofRecordsFailureAndReturnsGenericError() {
        allow();
        when(service.issueReauthenticationGrant(any())).thenReturn(
                new ReauthenticationUseCase.IssueResult(
                        ReauthenticationUseCase.Status.INVALID_PROOF,
                        null,
                        0,
                        null
                )
        );

        assertThatThrownBy(() -> controller.issue(
                jwt,
                request(),
                servletRequest
        )).isInstanceOf(ApiException.class);
        verify(risk).recordFailure("reauth:user-1", null);
    }

    @Test
    void invalidTargetAndRateLimitFailClosed() {
        allow();
        when(service.issueReauthenticationGrant(any())).thenReturn(
                new ReauthenticationUseCase.IssueResult(
                        ReauthenticationUseCase.Status.INVALID_TARGET,
                        null,
                        0,
                        null
                )
        );
        assertThatThrownBy(() -> controller.issue(
                jwt,
                request(),
                servletRequest
        )).isInstanceOf(ApiException.class);

        when(risk.allow("reauth:user-1", null)).thenReturn(false);
        when(risk.retryAfterSeconds()).thenReturn(900L);
        assertThatThrownBy(() -> controller.issue(
                jwt,
                request(),
                servletRequest
        )).isInstanceOf(ApiException.class);
    }

    @Test
    void riskStoreOutageFailsClosed() {
        when(risk.allow("reauth:user-1", null)).thenThrow(
                new LoginRiskUnavailableException(
                        new IllegalStateException("redis unavailable")
                )
        );

        assertThatThrownBy(() -> controller.issue(
                jwt,
                request(),
                servletRequest
        )).isInstanceOf(ApiException.class);
    }

    private void allow() {
        when(risk.allow("reauth:user-1", null)).thenReturn(true);
    }

    private static ReauthenticationGrantRequest request() {
        return new ReauthenticationGrantRequest(
                "correct password",
                "123456",
                ReauthenticationScope.TOPUP_MANUAL_APPROVAL,
                "topup",
                "topup-1"
        );
    }
}
