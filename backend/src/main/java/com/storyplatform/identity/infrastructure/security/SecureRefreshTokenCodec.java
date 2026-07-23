package com.storyplatform.identity.infrastructure.security;

import com.storyplatform.identity.application.port.RefreshTokenCodec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

public final class SecureRefreshTokenCodec implements RefreshTokenCodec {

    private static final int TOKEN_BYTES = 32;
    private static final Pattern TOKEN_FORMAT =
            Pattern.compile("[A-Za-z0-9_-]{43}");

    private final SecureRandom random;

    public SecureRefreshTokenCodec(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public IssuedRefreshToken issue() {
        byte[] entropy = new byte[TOKEN_BYTES];
        random.nextBytes(entropy);
        String value = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(entropy);
        return new IssuedRefreshToken(value, hash(value));
    }

    @Override
    public String hash(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    @Override
    public boolean isWellFormed(String rawToken) {
        return rawToken != null
                && TOKEN_FORMAT.matcher(rawToken).matches();
    }
}
