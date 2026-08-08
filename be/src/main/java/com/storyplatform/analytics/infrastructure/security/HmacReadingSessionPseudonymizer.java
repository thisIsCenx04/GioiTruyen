package com.storyplatform.analytics.infrastructure.security;

import com.storyplatform.analytics.application.port.ReadingSessionPseudonymizer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Produces stable, domain-separated, opaque pseudonyms for reading session
 * identifiers using HMAC-SHA256. The resulting hex string is 64 characters and
 * does not leak the original session UUID.
 *
 * <p>The key must be at least 32 bytes. The session identifier must be a valid
 * UUID (version 4, variant 2).
 */
public final class HmacReadingSessionPseudonymizer
        implements ReadingSessionPseudonymizer {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE
    );
    private static final String ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_KEY_BYTES = 32;

    private final byte[] keyBytes;

    public HmacReadingSessionPseudonymizer(byte[] keyBytes) {
        Objects.requireNonNull(keyBytes, "keyBytes must not be null");
        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "key must be at least " + MINIMUM_KEY_BYTES + " bytes"
            );
        }
        this.keyBytes = keyBytes.clone();
    }

    @Override
    public String pseudonymize(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (!UUID_PATTERN.matcher(sessionId).matches()) {
            throw new IllegalArgumentException(
                    "sessionId must be a valid UUID v4: " + sessionId
            );
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(keyBytes, ALGORITHM));
            byte[] digest = mac.doFinal(sessionId.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException(
                    "HMAC-SHA256 is unavailable", exception
            );
        }
    }
}
