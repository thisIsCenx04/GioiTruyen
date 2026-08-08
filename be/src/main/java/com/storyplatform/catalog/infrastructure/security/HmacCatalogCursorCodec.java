package com.storyplatform.catalog.infrastructure.security;

import com.storyplatform.catalog.application.port.CatalogCursorCodec;
import com.storyplatform.catalog.application.port.StoryRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public final class HmacCatalogCursorCodec implements CatalogCursorCodec {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final byte[] DOMAIN =
            "gioitruyen:catalog-cursor:v1".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key;

    public HmacCatalogCursorCodec(byte[] keyBytes) {
        Objects.requireNonNull(keyBytes, "keyBytes");
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "catalog cursor HMAC key requires at least 32 bytes"
            );
        }
        key = new SecretKeySpec(derive(keyBytes), ALGORITHM);
    }

    @Override
    public String encode(Cursor cursor) {
        Objects.requireNonNull(cursor, "cursor");
        String payload = String.join(
                "|",
                "v1",
                cursor.sort().name(),
                Long.toString(cursor.value().toEpochMilli()),
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
            return new Cursor(
                    StoryRepository.Sort.valueOf(payload[1]),
                    Instant.ofEpochMilli(Long.parseLong(payload[2])),
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
            throw new IllegalStateException("cannot sign catalog cursor",
                    exception);
        }
    }

    private static byte[] derive(byte[] rootKey) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(rootKey, ALGORITHM));
            return mac.doFinal(DOMAIN);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "cannot derive catalog cursor key",
                    exception
            );
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("catalog cursor is invalid");
    }
}
