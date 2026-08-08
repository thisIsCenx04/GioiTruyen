package com.storyplatform.catalog.infrastructure.security;

import com.storyplatform.catalog.application.port.ChapterCursorCodec;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public final class HmacChapterCursorCodec implements ChapterCursorCodec {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final byte[] DOMAIN =
            "gioitruyen:chapter-cursor:v1"
                    .getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key;

    public HmacChapterCursorCodec(byte[] rootKey) {
        Objects.requireNonNull(rootKey, "rootKey");
        if (rootKey.length < 32) {
            throw new IllegalArgumentException(
                    "chapter cursor HMAC key requires at least 32 bytes"
            );
        }
        key = new SecretKeySpec(derive(rootKey), ALGORITHM);
    }

    @Override
    public String encode(Cursor cursor) {
        Objects.requireNonNull(cursor, "cursor");
        String payload = String.join(
                "|",
                "v1",
                UUID.fromString(cursor.storyId()).toString(),
                Integer.toString(cursor.number()),
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
            int number = Integer.parseInt(payload[2]);
            if (number < 1) {
                throw invalid();
            }
            return new Cursor(
                    UUID.fromString(payload[1]).toString(),
                    number,
                    UUID.fromString(payload[3]).toString()
            );
        } catch (IllegalArgumentException exception) {
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
                    "cannot sign chapter cursor",
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
                    "cannot derive chapter cursor key",
                    exception
            );
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("chapter cursor is invalid");
    }
}
