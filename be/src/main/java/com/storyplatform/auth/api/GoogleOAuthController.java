package com.storyplatform.auth.api;

import com.storyplatform.auth.application.GoogleOAuthProperties;
import com.storyplatform.auth.application.GoogleOAuthService;
import com.storyplatform.auth.application.dto.AuthResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Browser-facing half of Google sign-in. Both endpoints answer with redirects
 * rather than JSON because the user agent navigates here directly.
 */
@RestController
@RequestMapping("/auth/oauth2/google")
@org.springframework.boot.context.properties.EnableConfigurationProperties(GoogleOAuthProperties.class)
public class GoogleOAuthController {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthController.class);

    private final GoogleOAuthService googleOAuthService;

    public GoogleOAuthController(GoogleOAuthService googleOAuthService) {
        this.googleOAuthService = googleOAuthService;
    }

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(
            @RequestParam(name = "returnTo", required = false) String returnTo
    ) {
        if (!googleOAuthService.isConfigured()) {
            return redirect(loginUrl("unconfigured"));
        }
        return redirect(googleOAuthService.buildAuthorizationUrl(returnTo));
    }

    /**
     * Tokens ride back in the URL fragment: the callback page reads them and
     * immediately rewrites the address bar, keeping them out of server logs and
     * the Referer header.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error
    ) {
        String returnTo = googleOAuthService.returnToFromState(state);

        if (error != null && !error.isBlank()) {
            log.warn("Google sign-in denied by provider: {}", error);
            return redirect(loginUrl("error"));
        }
        if (code == null || code.isBlank()) {
            return redirect(loginUrl("error"));
        }

        try {
            AuthResponse auth = googleOAuthService.completeSignIn(code);
            String fragment = "access_token=" + encode(auth.accessToken())
                    + "&refresh_token=" + encode(auth.refreshToken())
                    + "&returnTo=" + encode(returnTo);
            return redirect(frontendUrl("/auth/google/callback") + "#" + fragment);
        } catch (RuntimeException exception) {
            log.warn("Google sign-in failed: {}", exception.getMessage());
            return redirect(loginUrl("error"));
        }
    }

    private String loginUrl(String status) {
        return frontendUrl("/auth/login") + "?oauth=google&status=" + status;
    }

    private String frontendUrl(String path) {
        String base = googleOAuthService.frontendReturnBase();
        if (base == null || base.isBlank()) {
            return path;
        }
        return base.replaceAll("/+$", "") + path;
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(location).toString())
                .build();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
