package com.storyplatform.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.storyplatform.auth.application.AuthService;
import com.storyplatform.auth.application.LoginAttemptLimiter;
import com.storyplatform.auth.infrastructure.UserRepository;
import com.storyplatform.shared.api.ApiException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/** Covers the reject paths of the refresh exchange without standing up Spring. */
class AuthServiceRefreshTest {

    private NamedParameterJdbcTemplate jdbc;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        authService = new AuthService(
                mock(UserRepository.class),
                mock(PasswordEncoder.class),
                mock(JwtEncoder.class),
                jdbc,
                new LoginAttemptLimiter(),
                "2026-07-24"
        );
    }

    @Test
    void rejectsAnEmptyToken() {
        assertThatThrownBy(() -> authService.refresh("  "))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void rejectsATokenThatMatchesNoLiveRow() {
        when(jdbc.queryForList(anyString(), any(Map.class))).thenReturn(List.of());

        assertThatThrownBy(() -> authService.refresh("expired-or-revoked-token"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.code()).isEqualTo("auth.invalid_refresh_token");
                });
    }

    @Test
    void doesNotRevokeAnythingWhenTheTokenIsUnknown() {
        when(jdbc.queryForList(anyString(), any(Map.class))).thenReturn(List.of());

        assertThatThrownBy(() -> authService.refresh("unknown")).isInstanceOf(ApiException.class);
        verify(jdbc, never()).update(anyString(), any(Map.class));
    }

    @Test
    void looksTheTokenUpByHashRatherThanRawValue() {
        when(jdbc.queryForList(anyString(), any(Map.class))).thenReturn(List.of());
        String raw = "plain-refresh-token";

        assertThatThrownBy(() -> authService.refresh(raw)).isInstanceOf(ApiException.class);

        // The raw secret must never reach the query; only its SHA-256 digest may.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, ?>> params = ArgumentCaptor.forClass(Map.class);
        verify(jdbc).queryForList(anyString(), params.capture());
        assertThat(params.getValue().get("tokenHash"))
                .isNotEqualTo(raw)
                .asString()
                .hasSize(64)
                .matches("[0-9a-f]+");
    }
}
