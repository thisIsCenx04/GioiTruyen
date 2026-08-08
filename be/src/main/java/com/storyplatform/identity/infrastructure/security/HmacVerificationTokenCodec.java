package com.storyplatform.identity.infrastructure.security;

import com.storyplatform.identity.application.port.VerificationTokenCodec;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class HmacVerificationTokenCodec
        implements VerificationTokenCodec {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Pattern TOKEN_FORMAT = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}"
                    + "-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
                    + "\\.[A-Za-z0-9_-]{43}"
    );

    private final SecretKeySpec key;
    private final Supplier<UUID> idGenerator;

    public HmacVerificationTokenCodec(
            byte[] keyBytes,
            Supplier<UUID> idGenerator
    ) {
        Objects.requireNonNull(keyBytes, "keyBytes");
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "Verification HMAC key must contain at least 32 bytes"
            );
        }
        this.key = new SecretKeySpec(keyBytes.clone(), HMAC_ALGORITHM);
        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "idGenerator"
        );
    }

    @Override
    public IssuedVerificationToken issue(
            String userId,
            Instant expiresAt
    ) {
        String verificationId = idGenerator.get().toString();
        String rawToken = tokenForDelivery(
                verificationId,
                userId,
                expiresAt
        );
        return new IssuedVerificationToken(
                verificationId,
                hash(rawToken)
        );
    }

    @Override
    public String tokenForDelivery(
            String verificationId,
            String userId,
            Instant expiresAt
    ) {
        Objects.requireNonNull(verificationId, "verificationId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        String claims = verificationId
                + "\n" + userId
                + "\n" + expiresAt.getEpochSecond();
        return verificationId + "." + encode(hmac(claims));
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

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to create verification token",
                    exception
            );
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value);
    }
}
