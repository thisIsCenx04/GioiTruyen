package com.storyplatform.unit.identity.api;

import com.storyplatform.identity.api.MfaController;
import com.storyplatform.identity.api.MfaVerificationRequest;
import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.identity.application.LoginRiskUnavailableException;
import com.storyplatform.identity.application.MfaUseCase;
import com.storyplatform.identity.application.port.LoginRiskLimiter;
import com.storyplatform.shared.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MfaControllerTest {

    private final IdentityService service = mock(IdentityService.class);
    private final LoginRiskLimiter risk = mock(LoginRiskLimiter.class);
    private final HttpServletRequest request =
            mock(HttpServletRequest.class);
    private final MfaController controller =
            new MfaController(service, risk);
    private final Jwt jwt = new Jwt(
            "token",
            Instant.parse("2026-07-24T00:00:00Z"),
            Instant.parse("2026-07-24T00:10:00Z"),
            Map.of("alg", "HS256"),
            Map.of("sub", "user-1")
    );

    @Test
    void exposesEnrollmentMetadataWithoutProtectedSecret() {
        when(service.beginMfaEnrollment("user-1")).thenReturn(
                new MfaUseCase.EnrollmentChallenge(true, "BASE32")
        );

        var response = controller.challenge(jwt);

        assertThat(response.provisioningSecret()).isEqualTo("BASE32");
        assertThat(response.algorithm()).isEqualTo("SHA1");
        assertThat(response.digits()).isEqualTo(6);
        assertThat(response.periodSeconds()).isEqualTo(30);
    }

    @Test
    void returnsRecoveryCodesOnlyAfterActivation() {
        when(risk.allow("mfa:user-1", null)).thenReturn(true);
        when(service.verifyMfaEnrollment("user-1", "123456"))
                .thenReturn(new MfaUseCase.EnrollmentResult(
                        true,
                        List.of("recovery")
                ));

        assertThat(controller.verify(
                jwt,
                new MfaVerificationRequest("123456"),
                request
        ).recoveryCodes()).containsExactly("recovery");
    }

    @Test
    void rejectsInvalidEnrollmentCode() {
        when(risk.allow("mfa:user-1", null)).thenReturn(true);
        when(service.verifyMfaEnrollment("user-1", "000000"))
                .thenReturn(new MfaUseCase.EnrollmentResult(
                        false,
                        List.of()
                ));

        assertThatThrownBy(() -> controller.verify(
                jwt,
                new MfaVerificationRequest("000000"),
                request
        )).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsReplacingAnEnabledFactor() {
        when(service.beginMfaEnrollment("user-1")).thenReturn(
                new MfaUseCase.EnrollmentChallenge(false, null)
        );

        assertThatThrownBy(() -> controller.challenge(jwt))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rateLimitAndRiskOutageFailClosed() {
        when(risk.allow("mfa:user-1", null)).thenReturn(false);
        when(risk.retryAfterSeconds()).thenReturn(900L);
        assertThatThrownBy(() -> controller.verify(
                jwt,
                new MfaVerificationRequest("123456"),
                request
        )).isInstanceOf(ApiException.class);

        when(risk.allow("mfa:user-1", null)).thenThrow(
                new LoginRiskUnavailableException(
                        new IllegalStateException("redis unavailable")
                )
        );
        assertThatThrownBy(() -> controller.verify(
                jwt,
                new MfaVerificationRequest("123456"),
                request
        )).isInstanceOf(ApiException.class);
    }
}
