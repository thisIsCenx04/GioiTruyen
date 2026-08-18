package com.storyplatform.auth.api;

import com.storyplatform.auth.application.AuthService;
import com.storyplatform.auth.application.PasswordResetMailer;
import com.storyplatform.auth.application.dto.AuthResponse;
import com.storyplatform.auth.application.dto.LoginRequest;
import com.storyplatform.auth.application.dto.RegisterRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-up and sign-in sit directly under the API root: /login, /register,
 * /refresh. The endpoints are named after what they do, so an extra "auth"
 * segment in front added nothing a reader of the URL did not already know.
 */
@RestController
public class AuthController {

    private final AuthService authService;
    private final PasswordResetMailer passwordResetMailer;

    public AuthController(AuthService authService, PasswordResetMailer passwordResetMailer) {
        this.authService = authService;
        this.passwordResetMailer = passwordResetMailer;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Swaps a refresh token for a new pair so a session outlives the 30-minute access token. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    /**
     * Ends the session. The sign-out button sends no body, in which case every
     * refresh token on the account is revoked; a caller that names its own
     * token revokes only that one.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) LogoutRequest request
    ) {
        UUID userId = jwt == null || jwt.getSubject() == null
                ? null
                : UUID.fromString(jwt.getSubject());
        authService.logout(userId, request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Opens a password reset and emails the link.
     *
     * <p>Answers 204 whether or not the address has an account: a different
     * reply for a known email would let anyone test which addresses are
     * registered here. The token is never put in the response, where it would
     * let a caller reset any account they can name - only the mailbox owner
     * ever sees it.
     */
    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String token = authService.openPasswordReset(request.email());
        if (token != null) {
            passwordResetMailer.send(request.email().trim(), token);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    public record RefreshTokenRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    public record ForgotPasswordRequest(
            @NotBlank(message = "Chưa nhập email.")
            @Email(message = "Email không hợp lệ.")
            String email
    ) {
    }

    /** The same password rules registration enforces, so a reset cannot weaken one. */
    public record ResetPasswordRequest(
            @NotBlank(message = "Thiếu mã đặt lại mật khẩu.")
            String token,

            @NotBlank(message = "Chưa nhập mật khẩu.")
            @Size(min = 8, max = 128, message = "Mật khẩu phải từ 8 đến 128 ký tự.")
            @Pattern(
                    regexp = "^(?=.*[A-Z])(?=.*\\d).+$",
                    message = "Mật khẩu cần ít nhất một chữ in hoa và một chữ số."
            )
            String password
    ) {
    }
}
