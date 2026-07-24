package com.storyplatform.moderation.infrastructure.security;

import com.storyplatform.moderation.application.port
        .ModerationQueueCursorCodec;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public final class HmacModerationQueueCursorCodec
        implements ModerationQueueCursorCodec {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER =
            Base64.getUrlDecoder();
    private static final byte[] DOMAIN =
            "gioitruyen:moderation-queue-cursor:v1"
                    .getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key;

    public HmacModerationQueueCursorCodec(byte[] rootKey) {
        Objects.requireNonNull(rootKey, "rootKey");
        if (rootKey.length < 32) {
            throw new IllegalArgumentException(
                    "moderation cursor HMAC key requires at least 32 bytes"
            );
        }
        key = new SecretKeySpec(derive(rootKey), ALGORITHM);
    }

    @Override
    public String encode(Cursor cursor) {
        Objects.requireNonNull(cursor, "cursor");
        if (cursor.priority() < 0 || cursor.priority() > 100) {
            throw invalid();
        }
        String payload = String.join(
                "|",
                "v1",
                Integer.toString(cursor.priority()),
                Long.toString(cursor.submittedAt().toEpochMilli()),
                UUID.fromString(cursor.id()).toString()
        );
        String encoded = ENCODER.encodeToString(
                payload.getBytes(StandardCharsets.UTF_8)
        );
        return encoded + "." + ENCODER.encodeToString(sign(encoded));
    }

    @Override
    public Cursor decode(String value) {
        if (value == null || value.length() > 512) {
            throw invalid();
        }
        String[] token = value.split("\\.", -1);
        if (token.length != 2) {
            throw invalid();
        }
        try {
            if (!MessageDigest.isEqual(
                    sign(token[0]),
                    DECODER.decode(token[1])
            )) {
                throw invalid();
            }
            String[] payload = new String(
                    DECODER.decode(token[0]),
                    StandardCharsets.UTF_8
            ).split("\\|", -1);
            if (payload.length != 4 || !"v1".equals(payload[0])) {
                throw invalid();
            }
            int priority = Integer.parseInt(payload[1]);
            if (priority < 0 || priority > 100) {
                throw invalid();
            }
            return new Cursor(
                    priority,
                    Instant.ofEpochMilli(Long.parseLong(payload[2])),
                    UUID.fromString(payload[3]).toString()
            );
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "cannot sign moderation queue cursor",
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
                    "cannot derive moderation queue cursor key",
                    exception
            );
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(
                "moderation queue cursor is invalid"
        );
    }
}
