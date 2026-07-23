package com.storyplatform.identity.infrastructure.security;

import com.storyplatform.identity.application.port
        .ReauthenticationTokenCodec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

public final class SecureReauthenticationTokenCodec
        implements ReauthenticationTokenCodec {

    private static final Pattern FORMAT =
            Pattern.compile("[A-Za-z0-9_-]{43}");
    private final SecureRandom random;

    public SecureReauthenticationTokenCodec(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public IssuedToken issue() {
        byte[] entropy = new byte[32];
        random.nextBytes(entropy);
        String value = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(entropy);
        return new IssuedToken(value, hash(value));
    }

    @Override
    public String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            rawToken.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    @Override
    public boolean isWellFormed(String rawToken) {
        return rawToken != null && FORMAT.matcher(rawToken).matches();
    }
}
