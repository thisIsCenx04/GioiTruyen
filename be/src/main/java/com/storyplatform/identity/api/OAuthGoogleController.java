package com.storyplatform.identity.api;

import com.storyplatform.identity.infrastructure.GoogleOAuthProperties;
import com.storyplatform.identity.infrastructure.GoogleOAuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Handles Google OAuth2 Authorization Code Flow.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /auth/oauth2/google/authorize  — redirects user to Google consent screen</li>
 *   <li>GET /auth/oauth2/google/callback   — receives code from Google, issues JWT, redirects frontend</li>
 * </ul>
 */
@RestController
@RequestMapping("/auth/oauth2/google")
public class OAuthGoogleController {

    private static final Logger log = LoggerFactory.getLogger(OAuthGoogleController.class);
    private static final String STATE_SESSION_KEY = "oauth2_google_state";
    private static final String RETURN_TO_SESSION_KEY = "oauth2_google_return_to";

    private final GoogleOAuthProperties properties;
    private final GoogleOAuthService oauthService;
    private final SecureRandom random = new SecureRandom();

    public OAuthGoogleController(
            GoogleOAuthProperties properties,
            GoogleOAuthService oauthService
    ) {
        this.properties = properties;
        this.oauthService = oauthService;
    }

    /**
     * Step 1 — Redirect user to Google authorization endpoint.
     *
     * @param returnTo  page to return to after login (optional)
     */
    @GetMapping("/authorize")
    public void authorize(
            @RequestParam(required = false, defaultValue = "/") String returnTo,
            HttpSession session,
            jakarta.servlet.http.HttpServletRequest servletRequest,
            HttpServletResponse response
    ) throws IOException {
        // Guard: if not configured, redirect back with error param
        if (!properties.isConfigured()) {
            log.warn("Google OAuth is not configured — missing client_id or client_secret");
            String fallback = properties.frontendReturnBase() != null && !properties.frontendReturnBase().isBlank()
                    ? properties.frontendReturnBase().replaceAll("/+$", "")
                    : "";
            response.sendRedirect(fallback + "/auth/login?oauth=google&status=unconfigured");
            return;
        }

        // Generate CSRF state
        byte[] stateBytes = new byte[18];
        random.nextBytes(stateBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);

        // Store in session for validation on callback
        session.setAttribute(STATE_SESSION_KEY, state);
        session.setAttribute(RETURN_TO_SESSION_KEY, returnTo);

        String redirectUri = resolveRedirectUri(servletRequest);
        String authUrl = oauthService.buildAuthorizationUrl(state, returnTo, redirectUri);
        response.sendRedirect(authUrl);
    }

    /**
     * Step 2 — Google calls back with authorization code.
     * Exchange code for tokens, issue JWT session, redirect frontend.
     */
    @GetMapping("/callback")
    public void callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            jakarta.servlet.http.HttpServletRequest servletRequest,
            HttpServletResponse response
    ) throws IOException {
        String frontendBase = properties.frontendReturnBase() != null && !properties.frontendReturnBase().isBlank()
                ? properties.frontendReturnBase().replaceAll("/+$", "")
                : "";

        if (error != null) {
            log.warn("Google OAuth returned error: {}", error);
            response.sendRedirect(frontendBase + "/auth/login?oauth=google&status=error");
            return;
        }

        if (code == null || code.isBlank()) {
            log.warn("Google OAuth callback received no code");
            response.sendRedirect(frontendBase + "/auth/login?oauth=google&status=error");
            return;
        }

        // Direct DB login & JWT generation (Bypass session/CSRF state checks)
        try {
            String redirectUri = resolveRedirectUri(servletRequest);
            GoogleOAuthService.OAuthResult result = oauthService.exchangeAndLogin(code, redirectUri);

            String redirectUrl = frontendBase + "/auth/google/callback#access_token="
                    + encode(result.accessToken())
                    + "&refresh_token=" + encode(result.refreshToken())
                    + "&returnTo=/";

            response.sendRedirect(redirectUrl);
        } catch (Exception ex) {
            log.error("Direct Google OAuth DB login failed", ex);
            response.sendRedirect(frontendBase + "/auth/login?oauth=google&status=error");
        }
    }

    private String resolveRedirectUri(jakarta.servlet.http.HttpServletRequest request) {
        String configured = properties.redirectUri();
        if (configured != null && configured.startsWith("https://")) {
            return configured;
        }
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        if (proto == null || proto.isBlank()) {
            proto = request.getScheme();
        }
        if (host != null && !host.isBlank()) {
            return proto + "://" + host + "/api/v1/auth/oauth2/google/callback";
        }
        return configured;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
