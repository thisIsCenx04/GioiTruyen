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

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            NamedParameterJdbcTemplate jdbc
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jdbc = jdbc;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw conflict("Email already exists");
        }
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw conflict("Username already exists");
        }

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(request.email());
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.username());
        user.setRole(UserRole.READER);
        user.setStatus(UserStatus.ACTIVE);
        
        Instant now = Instant.now();
        user.setEmailVerifiedAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        user = userRepository.save(user);
        createDefaultUserRows(user.getId(), now);

        return issueAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(this::invalidCredentials);

        if (!passwordMatches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "auth.user_disabled", "User disabled", "User is not active");
        }

        Instant now = Instant.now();
        user.setLastLoginAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

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

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "auth.conflict", message, message);
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
