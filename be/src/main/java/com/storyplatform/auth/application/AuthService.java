package com.storyplatform.auth.application;

import com.storyplatform.auth.application.dto.AuthResponse;
import com.storyplatform.auth.application.dto.LoginRequest;
import com.storyplatform.auth.application.dto.RegisterRequest;
import com.storyplatform.auth.application.dto.UserDto;
import com.storyplatform.auth.domain.User;
import com.storyplatform.auth.domain.UserRole;
import com.storyplatform.auth.domain.UserStatus;
import com.storyplatform.auth.infrastructure.UserRepository;
import com.storyplatform.shared.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(30);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final NamedParameterJdbcTemplate jdbc;
    private final LoginAttemptLimiter loginAttemptLimiter;
    /** Stored with each sign-up so it stays clear what was agreed to. */
    private final String consentVersion;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            NamedParameterJdbcTemplate jdbc,
            LoginAttemptLimiter loginAttemptLimiter,
            @org.springframework.beans.factory.annotation.Value(
                    "${app.identity.registration.current-consent-version}") String consentVersion
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jdbc = jdbc;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.consentVersion = consentVersion;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw conflict("Email này đã được đăng ký. Hãy đăng nhập hoặc dùng email khác.");
        }

        // The sign-up form only asks for an email, so the username is derived
        // from it and made unique here rather than being demanded of the reader.
        String username = request.username() == null || request.username().isBlank()
                ? uniqueUsernameFrom(request.email())
                : request.username().trim();
        if (userRepository.findByUsername(username).isPresent()) {
            throw conflict("Tên đăng nhập \"%s\" đã có người dùng.".formatted(username));
        }

        User user = new User();
        UUID userId = UUID.randomUUID();
        user.setId(userId);
        user.setEmail(request.email());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(username);
        user.setRole(UserRole.READER);
        user.setStatus(UserStatus.ACTIVE);

        Instant now = Instant.now();
        user.setEmailVerifiedAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        // The id is assigned here, so Spring Data JDBC treats the entity as an
        // existing row and save() emits an UPDATE that matches nothing - which
        // made every sign-up fail with a 500. An explicit INSERT is required.
        jdbc.update(
                """
                        INSERT INTO users (id, email, username, password_hash, display_name,
                                           role, status, email_verified_at,
                                           terms_accepted_at, terms_version,
                                           created_at, updated_at)
                        VALUES (:id, :email, :username, :passwordHash, :displayName,
                                :role, :status, :emailVerifiedAt,
                                :termsAcceptedAt, :termsVersion,
                                :createdAt, :updatedAt)
                        """,
                Map.ofEntries(
                        Map.entry("id", userId.toString()),
                        Map.entry("email", user.getEmail()),
                        Map.entry("username", user.getUsername()),
                        Map.entry("passwordHash", user.getPasswordHash()),
                        Map.entry("displayName", user.getDisplayName()),
                        Map.entry("role", user.getRole().name()),
                        Map.entry("status", user.getStatus().name()),
                        Map.entry("emailVerifiedAt", java.sql.Timestamp.from(now)),
                        // Validation already refused anything but true, so the
                        // moment the row is written is the moment they agreed.
                        Map.entry("termsAcceptedAt", java.sql.Timestamp.from(now)),
                        Map.entry("termsVersion", consentVersion),
                        Map.entry("createdAt", java.sql.Timestamp.from(now)),
                        Map.entry("updatedAt", java.sql.Timestamp.from(now))
                )
        );
        createDefaultUserRows(userId, now);

        return issueAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Keyed on the account, not the caller: RateLimitingFilter already caps
        // per-IP traffic, but without this a botnet could spread guesses against
        // one account across enough addresses to stay under that cap.
        String limiterKey = request.email() == null ? "" : request.email().trim().toLowerCase();
        Instant attemptedAt = Instant.now();
        long retryAfter = loginAttemptLimiter.retryAfterSeconds(limiterKey, attemptedAt);
        if (retryAfter > 0) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "auth.too_many_attempts",
                    "Too many attempts",
                    "Đăng nhập sai quá nhiều lần. Vui lòng thử lại sau %d phút."
                            .formatted(Math.max(1, retryAfter / 60)),
                    Duration.ofSeconds(retryAfter)
            );
        }

        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null || !passwordMatches(request.password(), user.getPasswordHash())) {
            loginAttemptLimiter.recordFailure(limiterKey, attemptedAt);
            throw invalidCredentials();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "auth.user_disabled", "User disabled", "User is not active");
        }
        loginAttemptLimiter.recordSuccess(limiterKey);

        Instant now = Instant.now();
        user.setLastLoginAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        return issueAuthResponse(user);
    }

    /**
     * Exchanges a refresh token for a fresh token pair. The presented token is
     * revoked in the same transaction as the new one is issued, so a leaked
     * token is usable at most once.
     */
    @Transactional
    public AuthResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw invalidRefreshToken();
        }

        Instant now = Instant.now();
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                        SELECT id, user_id
                        FROM refresh_tokens
                        WHERE token_hash = :tokenHash
                          AND revoked_at IS NULL
                          AND expires_at > :now
                        """,
                Map.of("tokenHash", sha256(refreshToken), "now", now)
        );
        if (rows.isEmpty()) {
            throw invalidRefreshToken();
        }

        jdbc.update(
                "UPDATE refresh_tokens SET revoked_at = :now WHERE id = :id",
                Map.of("now", now, "id", String.valueOf(rows.get(0).get("id")))
        );

        User user = userRepository.findById(UUID.fromString(String.valueOf(rows.get(0).get("user_id"))))
                .orElseThrow(this::invalidRefreshToken);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "auth.user_disabled", "User disabled", "User is not active");
        }

        return issueAuthResponse(user);
    }

    /** Package-private so the Google sign-in flow can mint the same token pair. */
    AuthResponse issueAuthResponse(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ACCESS_TOKEN_TTL);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("gioitruyen")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim("scope", user.getRole().name())
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail())
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(() -> "HS256").build(),
                claims
        )).getTokenValue();
        String refreshToken = randomToken();
        jdbc.update(
                """
                        INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, created_at)
                        VALUES (:id, :userId, :tokenHash, :expiresAt, :createdAt)
                        """,
                Map.of(
                        "id", UUID.randomUUID().toString(),
                        "userId", user.getId().toString(),
                        "tokenHash", sha256(refreshToken),
                        "expiresAt", now.plus(REFRESH_TOKEN_TTL),
                        "createdAt", now
                )
        );
        return new AuthResponse(accessToken, refreshToken, mapToDto(user));
    }

    /** Package-private so the Google sign-in flow can seed the same side tables. */
    void createDefaultUserRows(UUID userId, Instant now) {
        jdbc.update(
                """
                        INSERT INTO user_profiles (user_id, updated_at)
                        VALUES (:userId, :updatedAt)
                        ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)
                        """,
                Map.of("userId", userId.toString(), "updatedAt", now)
        );
        jdbc.update(
                """
                        INSERT INTO user_settings (user_id, updated_at)
                        VALUES (:userId, :updatedAt)
                        ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)
                        """,
                Map.of("userId", userId.toString(), "updatedAt", now)
        );
        jdbc.update(
                """
                        INSERT INTO wallets (id, user_id, coin_balance, gem_balance, updated_at)
                        VALUES (:id, :userId, 0, 0, :updatedAt)
                        ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)
                        """,
                Map.of("id", UUID.randomUUID().toString(), "userId", userId.toString(), "updatedAt", now)
        );
    }

    private boolean passwordMatches(String rawPassword, String storedPassword) {
        if (storedPassword == null || storedPassword.isBlank()) {
            return false;
        }
        if (storedPassword.startsWith("$2")) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return MessageDigest.isEqual(
                rawPassword.getBytes(StandardCharsets.UTF_8),
                storedPassword.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * Builds a username from the email's local part, adding a numeric suffix
     * until it is free. Bounded so a burst of similar addresses cannot spin.
     */
    private String uniqueUsernameFrom(String email) {
        String base = email.split("@")[0]
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "")
                .trim();
        if (base.length() < 3) {
            base = "doc-gia" + base;
        }
        if (base.length() > 90) {
            base = base.substring(0, 90);
        }
        if (userRepository.findByUsername(base).isEmpty()) {
            return base;
        }
        for (int suffix = 1; suffix <= 999; suffix++) {
            String candidate = base + suffix;
            if (userRepository.findByUsername(candidate).isEmpty()) {
                return candidate;
            }
        }
        // Practically unreachable; a random tail guarantees termination.
        return base + UUID.randomUUID().toString().substring(0, 6);
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "auth.conflict", message, message);
    }

    private ApiException invalidRefreshToken() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "auth.invalid_refresh_token",
                "Invalid refresh token",
                "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        );
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "auth.invalid_credentials", "Invalid credentials", "Invalid credentials");
    }

    private UserDto mapToDto(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getStatus()
        );
    }
}
