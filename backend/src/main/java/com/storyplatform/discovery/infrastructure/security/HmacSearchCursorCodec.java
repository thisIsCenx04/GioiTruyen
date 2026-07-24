package com.storyplatform.discovery.infrastructure.security;

import com.storyplatform.discovery.application.port.SearchCursorCodec;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;

public final class HmacSearchCursorCodec implements SearchCursorCodec {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final byte[] DOMAIN =
            "gioitruyen:search-cursor:v1"
                    .getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key;

    public HmacSearchCursorCodec(byte[] rootKey) {
        Objects.requireNonNull(rootKey, "rootKey");
        if (rootKey.length < 32) {
            throw new IllegalArgumentException(
                    "search cursor HMAC key requires at least 32 bytes"
            );
        }
        key = new SecretKeySpec(derive(rootKey), ALGORITHM);
    }

    @Override
    public String encode(String atlasToken) {
        if (atlasToken == null || atlasToken.isBlank()
                || atlasToken.length() > 1024) {
            throw invalid();
        }
        String payload = ENCODER.encodeToString(
                ("v1|" + atlasToken).getBytes(StandardCharsets.UTF_8)
        );
        return payload + "." + ENCODER.encodeToString(sign(payload));
    }

    @Override
    public String decode(String cursor) {
        if (cursor == null || cursor.length() > 2048) {
            throw invalid();
        }
        String[] token = cursor.split("\\.", -1);
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
            String payload = new String(
                    DECODER.decode(token[0]),
                    StandardCharsets.UTF_8
            );
            if (!payload.startsWith("v1|")
                    || payload.length() < 4
                    || payload.length() > 1027) {
                throw invalid();
            }
            return payload.substring(3);
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
                    "cannot sign search cursor",
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
                    "cannot derive search cursor key",
                    exception
            );
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("search cursor is invalid");
    }
}
