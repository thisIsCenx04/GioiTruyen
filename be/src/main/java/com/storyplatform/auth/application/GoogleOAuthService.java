package com.storyplatform.auth.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storyplatform.auth.application.dto.AuthResponse;
import com.storyplatform.auth.domain.User;
import com.storyplatform.auth.domain.UserRole;
import com.storyplatform.auth.domain.UserStatus;
import com.storyplatform.auth.infrastructure.UserRepository;
import com.storyplatform.shared.api.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleOAuthService {

    private static final String AUTHORIZE_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final String SCOPES = "openid email profile";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final GoogleOAuthProperties properties;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final SecureRandom random = new SecureRandom();

    public GoogleOAuthService(
            GoogleOAuthProperties properties,
            AuthService authService,
            UserRepository userRepository,
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.authService = authService;
        this.userRepository = userRepository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public String frontendReturnBase() {
        return properties.getFrontendReturnBase();
    }

    /**
     * Builds the consent-screen URL. {@code returnTo} travels inside the state
     * value so the callback can send the browser back where it started without
     * trusting a separate query parameter.
     */
    public String buildAuthorizationUrl(String returnTo) {
        String state = encodeState(returnTo);
        return AUTHORIZE_ENDPOINT
                + "?client_id=" + encode(properties.getClientId())
                + "&redirect_uri=" + encode(properties.getRedirectUri())
                + "&response_type=code"
                + "&scope=" + encode(SCOPES)
                + "&state=" + encode(state)
                + "&access_type=offline"
                + "&include_granted_scopes=true"
                + "&prompt=select_account";
    }

    /** Recovers the {@code returnTo} path packed into the state value. */
    public String returnToFromState(String state) {
        if (state == null || state.isBlank()) {
            return "/";
        }
        int separator = state.indexOf(':');
        if (separator < 0 || separator + 1 >= state.length()) {
            return "/";
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(state.substring(separator + 1)),
                    StandardCharsets.UTF_8);
            return safeInternalPath(decoded);
        } catch (IllegalArgumentException exception) {
            return "/";
        }
    }

    @Transactional
    public AuthResponse completeSignIn(String code) {
        GoogleProfile profile = fetchProfile(code);
        User user = upsertUser(profile);
        linkAuthAccount(user.getId(), profile.subject());
        return authService.issueAuthResponse(user);
    }

    private GoogleProfile fetchProfile(String code) {
        String accessToken = exchangeCodeForAccessToken(code);
        JsonNode userInfo = getJson(USERINFO_ENDPOINT, accessToken);

        String email = text(userInfo, "email");
        String subject = text(userInfo, "sub");
        if (email == null || email.isBlank() || subject == null || subject.isBlank()) {
            throw upstreamFailure("Google account did not expose an email address");
        }
        return new GoogleProfile(
                subject,
                email.trim().toLowerCase(Locale.ROOT),
                text(userInfo, "name"),
                text(userInfo, "picture"),
                Boolean.parseBoolean(text(userInfo, "email_verified"))
        );
    }

    private String exchangeCodeForAccessToken(String code) {
        String form = "code=" + encode(code)
                + "&client_id=" + encode(properties.getClientId())
                + "&client_secret=" + encode(properties.getClientSecret())
                + "&redirect_uri=" + encode(properties.getRedirectUri())
                + "&grant_type=authorization_code";

        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_ENDPOINT))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();

        JsonNode body = send(request);
        String accessToken = text(body, "access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw upstreamFailure("Google did not return an access token");
        }
        return accessToken;
    }

    private JsonNode getJson(String endpoint, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        return send(request);
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw upstreamFailure("Google responded with status " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw upstreamFailure("Could not reach Google: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw upstreamFailure("Google sign-in was interrupted");
        }
    }

    /**
     * Matches on email so an account first created with a password can later sign
     * in with Google. Existing local credentials are left untouched.
     */
    private User upsertUser(GoogleProfile profile) {
        Instant now = Instant.now();
        Optional<User> existing = userRepository.findByEmail(profile.email());

        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new ApiException(HttpStatus.FORBIDDEN, "auth.user_disabled",
                        "User disabled", "User is not active");
            }
            if (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) {
                user.setAvatarUrl(profile.pictureUrl());
            }
            if (user.getEmailVerifiedAt() == null && profile.emailVerified()) {
                user.setEmailVerifiedAt(now);
            }
            user.setLastLoginAt(now);
            user.setUpdatedAt(now);
            userRepository.save(user);
            return user;
        }

        UUID id = UUID.randomUUID();
        String username = uniqueUsername(profile.email());
        String displayName = profile.displayName() == null || profile.displayName().isBlank()
                ? profile.email().split("@")[0]
                : profile.displayName();

        // Ids are assigned here, so an explicit INSERT is used; repository.save()
        // would treat the populated id as an existing row and emit an UPDATE.
        Map<String, Object> parameters = new java.util.HashMap<>();
        parameters.put("id", id.toString());
        parameters.put("email", profile.email());
        parameters.put("username", username);
        parameters.put("displayName", displayName);
        parameters.put("avatarUrl", profile.pictureUrl());
        parameters.put("role", UserRole.READER.name());
        parameters.put("status", UserStatus.ACTIVE.name());
        parameters.put("emailVerifiedAt", profile.emailVerified() ? now : null);
        parameters.put("lastLoginAt", now);
        parameters.put("createdAt", now);
        parameters.put("updatedAt", now);

        jdbc.update(
                """
                        INSERT INTO users (id, email, username, password_hash, display_name, avatar_url,
                                           role, status, email_verified_at, last_login_at, created_at, updated_at)
                        VALUES (:id, :email, :username, NULL, :displayName, :avatarUrl,
                                :role, :status, :emailVerifiedAt, :lastLoginAt, :createdAt, :updatedAt)
                        """,
                parameters
        );

        authService.createDefaultUserRows(id, now);
        return userRepository.findById(id).orElseThrow(() ->
                upstreamFailure("Could not load the account created for this Google profile"));
    }

    private void linkAuthAccount(UUID userId, String providerAccountId) {
        jdbc.update(
                """
                        INSERT INTO auth_accounts (id, user_id, provider, provider_account_id, created_at)
                        VALUES (:id, :userId, 'GOOGLE', :providerAccountId, :createdAt)
                        ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
                        """,
                Map.of(
                        "id", UUID.randomUUID().toString(),
                        "userId", userId.toString(),
                        "providerAccountId", providerAccountId,
                        "createdAt", Instant.now()
                )
        );
    }

    /** users.username is UNIQUE, so a collision gets a short numeric suffix. */
    private String uniqueUsername(String email) {
        String base = email.split("@")[0]
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
        if (base.isBlank()) {
            base = "user";
        }
        String candidate = base;
        for (int attempt = 1; attempt <= 50; attempt++) {
            if (userRepository.findByUsername(candidate).isEmpty()) {
                return candidate;
            }
            candidate = base + attempt;
        }
        return base + UUID.randomUUID().toString().substring(0, 8);
    }

    private String encodeState(String returnTo) {
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(safeInternalPath(returnTo).getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nonce) + ":" + payload;
    }

    /** Blocks open redirects: only same-site absolute paths are accepted. */
    static String safeInternalPath(String value) {
        if (value == null || value.isBlank()) {
            return "/";
        }
        String trimmed = value.trim();
        return trimmed.startsWith("/") && !trimmed.startsWith("//") ? trimmed : "/";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static ApiException upstreamFailure(String detail) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "auth.google_failed",
                "Google sign-in failed", detail);
    }

    private record GoogleProfile(
            String subject,
            String email,
            String displayName,
            String pictureUrl,
            boolean emailVerified
    ) {}
}
