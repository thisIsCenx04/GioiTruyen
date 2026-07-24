package com.storyplatform.analytics.infrastructure.security;

import com.storyplatform.analytics.application.port
        .ReadingSessionPseudonymizer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class HmacReadingSessionPseudonymizer
        implements ReadingSessionPseudonymizer {

    private static final String ALGORITHM = "HmacSHA256";
    private static final byte[] DOMAIN =
            "gioitruyen:analytics:reading-session:v1"
                    .getBytes(StandardCharsets.UTF_8);
    private final SecretKeySpec key;

    public HmacReadingSessionPseudonymizer(byte[] rootKey) {
        Objects.requireNonNull(rootKey, "rootKey");
        if (rootKey.length < 32) {
            throw new IllegalArgumentException(
                    "analytics pseudonym key requires 32 bytes"
            );
        }
        key = new SecretKeySpec(derive(rootKey), ALGORITHM);
    }

    @Override
    public String pseudonymize(String sessionId) {
        String normalized;
        try {
            normalized = UUID.fromString(sessionId).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "reading session identifier is invalid",
                    exception
            );
        }
        return HexFormat.of().formatHex(sign(
                normalized.getBytes(StandardCharsets.UTF_8)
        ));
    }

    private byte[] sign(byte[] value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(value);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "cannot pseudonymize reading session",
                    exception
            );
        }
    }

    private static byte[] derive(byte[] rootKey) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(rootKey, ALGORITHM));
            return mac.doFinal(DOMAIN);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "cannot derive analytics pseudonym key",
                    exception
            );
        }
    }
}
