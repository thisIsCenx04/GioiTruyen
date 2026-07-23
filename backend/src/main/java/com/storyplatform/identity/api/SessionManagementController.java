package com.storyplatform.identity.api;

import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.shared.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class SessionManagementController {

    private final IdentityService identityService;

    public SessionManagementController(IdentityService identityService) {
        this.identityService = Objects.requireNonNull(
                identityService,
                "identityService"
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Jwt jwt
    ) {
        identityService.logout(jwt.getSubject(), sessionId(jwt));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public SessionListResponse list(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return new SessionListResponse(
                identityService.listSessions(
                                jwt.getSubject(),
                                sessionId(jwt)
                        )
                        .stream()
                        .map(SessionResponse::from)
                        .toList()
        );
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String sessionId
    ) {
        identityService.revokeSession(
                jwt.getSubject(),
                requireSessionId(sessionId)
        );
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<Void> revokeAll(
            @AuthenticationPrincipal Jwt jwt
    ) {
        identityService.revokeAllSessions(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    private static String sessionId(Jwt jwt) {
        return jwt.getClaimAsString("sid");
    }

    private static String requireSessionId(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SESSION_ID_INVALID",
                    "Session request rejected",
                    "The session identifier is invalid."
            );
        }
    }
}
