package com.storyplatform.unit.identity.api;

import com.storyplatform.identity.api.ForgotPasswordRequest;
import com.storyplatform.identity.api.PasswordResetController;
import com.storyplatform.identity.api.ResetPasswordRequest;
import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.identity.application.PasswordResetOutcome;
import com.storyplatform.shared.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class PasswordResetControllerTest {

    private final IdentityService service = mock(IdentityService.class);
    private final PasswordResetController controller =
            new PasswordResetController(service);

    @Test
    void forgotAlwaysReturnsAccepted() {
        HttpServletRequest servletRequest =
                mock(HttpServletRequest.class);

        var response = controller.forgot(
                new ForgotPasswordRequest("reader@example.com"),
                servletRequest
        );

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).containsEntry("status", "ACCEPTED");
        verify(service).requestPasswordReset(
                eq("reader@example.com"),
                anyString()
        );
    }

    @Test
    void resetReturnsNoContentOnSuccess() {
        when(service.resetPassword("token", "new secure password"))
                .thenReturn(PasswordResetOutcome.RESET);

        assertThat(controller.reset(new ResetPasswordRequest(
                "token",
                "new secure password"
        )).getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void resetMapsInvalidAndWeakOutcomesToSafeErrors() {
        when(service.resetPassword("bad", "new secure password"))
                .thenReturn(PasswordResetOutcome.INVALID_OR_EXPIRED);
        when(service.resetPassword("token", "weak password"))
                .thenReturn(PasswordResetOutcome.WEAK_PASSWORD);

        assertThatThrownBy(() -> controller.reset(
                new ResetPasswordRequest("bad", "new secure password")
        )).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> controller.reset(
                new ResetPasswordRequest("token", "weak password")
        )).isInstanceOf(ApiException.class);
    }
}
