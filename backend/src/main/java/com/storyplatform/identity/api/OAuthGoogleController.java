package com.storyplatform.identity.api;

import com.storyplatform.identity.infrastructure.GoogleOAuthProperties;
import com.storyplatform.identity.infrastructure.GoogleOAuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
            @RequestParam(required = false, defaultValue = "/account/sessions") String returnTo,
            HttpSession session,
            HttpServletResponse response
    ) throws IOException {
        // Guard: if not configured, redirect back with error param
        if (!properties.isConfigured()) {
            log.warn("Google OAuth is not configured — missing client_id or client_secret");
            String fallback = properties.frontendReturnBase() != null
                    ? properties.frontendReturnBase() : "http://localhost:3000";
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

        String authUrl = oauthService.buildAuthorizationUrl(state, returnTo);
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
            HttpSession session,
            HttpServletResponse response
    ) throws IOException {
        String frontendBase = properties.frontendReturnBase() != null
                ? properties.frontendReturnBase() : "http://localhost:3000";

        // Handle error from Google
        if (error != null) {
            log.warn("Google OAuth callback error: {}", error);
            response.sendRedirect(frontendBase + "/auth/login?oauth=google&status=error");
            return;
        }

        // Validate state (CSRF check)
        String expectedState = (String) session.getAttribute(STATE_SESSION_KEY);
        if (expectedState == null || !expectedState.equals(state)) {
            log.warn("Google OAuth state mismatch — possible CSRF attack");
            response.sendRedirect(frontendBase + "/auth/login?oauth=google&status=error");
            return;
        }

        // Retrieve returnTo
        String returnTo = (String) session.getAttribute(RETURN_TO_SESSION_KEY);
        if (returnTo == null || returnTo.isBlank()) {
            returnTo = "/account/sessions";
        }
        session.removeAttribute(STATE_SESSION_KEY);
        session.removeAttribute(RETURN_TO_SESSION_KEY);

        // Exchange code → issue session
        try {
            GoogleOAuthService.OAuthResult result = oauthService.exchangeAndLogin(code);

            // Redirect frontend with tokens as query params
            // (Frontend reads and stores them via existing auth client mechanism)
            String redirectUrl = frontendBase + returnTo
                    + "?access_token=" + encode(result.accessToken())
                    + "&refresh_token=" + encode(result.refreshToken())
                    + "&expires_in=" + result.expiresInSeconds()
                    + "&token_type=Bearer";

            response.sendRedirect(redirectUrl);
        } catch (Exception ex) {
            log.error("Google OAuth exchange failed", ex);
            response.sendRedirect(frontendBase + "/auth/login?oauth=google&status=error");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
