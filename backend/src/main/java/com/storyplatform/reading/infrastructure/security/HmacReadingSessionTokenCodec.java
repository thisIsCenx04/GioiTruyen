package com.storyplatform.reading.infrastructure.security;

import com.storyplatform.reading.application.port.ReadingSessionTokenCodec;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public final class HmacReadingSessionTokenCodec
        implements ReadingSessionTokenCodec {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER =
            Base64.getUrlDecoder();
    private static final byte[] TOKEN_DOMAIN =
            bytes("gioitruyen:reading-session-token:v1");
    private static final byte[] ACTOR_DOMAIN =
            bytes("gioitruyen:reading-session-actor:v1");
    private final SecretKeySpec tokenKey;
    private final SecretKeySpec actorKey;

    public HmacReadingSessionTokenCodec(byte[] rootKey) {
        if (rootKey == null || rootKey.length < 32) {
            throw new IllegalArgumentException(
                    "reading session token key requires 32 bytes"
            );
        }
        tokenKey = new SecretKeySpec(
                derive(rootKey, TOKEN_DOMAIN),
                ALGORITHM
        );
        actorKey = new SecretKeySpec(
                derive(rootKey, ACTOR_DOMAIN),
                ALGORITHM
        );
    }

    @Override
    public String fingerprint(String subject) {
        if (subject == null || subject.isBlank()
                || subject.length() > 512) {
            throw new IllegalArgumentException(
                    "reading actor subject is invalid"
            );
        }
        return ENCODER.encodeToString(sign(actorKey, subject));
    }

    @Override
    public String issue(Claims claims) {
        Objects.requireNonNull(claims, "claims");
        if (!claims.expiresAt().isAfter(claims.issuedAt())) {
            throw invalid();
        }
        String payload = String.join(
                "|",
                "v1",
                uuid(claims.sessionId()),
                uuid(claims.storyId()),
                uuid(claims.chapterId()),
                requireActorRef(claims.actorRef()),
                claims.issuedAt().toString(),
                claims.expiresAt().toString()
        );
        String encoded = ENCODER.encodeToString(bytes(payload));
        return encoded + "." + ENCODER.encodeToString(
                sign(tokenKey, encoded)
        );
    }

    @Override
    public Claims verify(String token, Instant now) {
        Objects.requireNonNull(now, "now");
        if (token == null || token.length() > 1024) {
            throw invalid();
        }
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(
                    sign(tokenKey, parts[0]),
                    DECODER.decode(parts[1])
            )) {
                throw invalid();
            }
            String[] payload = new String(
                    DECODER.decode(parts[0]),
                    StandardCharsets.UTF_8
            ).split("\\|", -1);
            if (payload.length != 7 || !"v1".equals(payload[0])) {
                throw invalid();
            }
            Claims claims = new Claims(
                    uuid(payload[1]),
                    uuid(payload[2]),
                    uuid(payload[3]),
                    requireActorRef(payload[4]),
                    Instant.parse(payload[5]),
                    Instant.parse(payload[6])
            );
            if (!claims.expiresAt().isAfter(claims.issuedAt())
                    || !now.isBefore(claims.expiresAt())) {
                throw invalid();
            }
            return claims;
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    private static String requireActorRef(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) {
            throw invalid();
        }
        return value;
    }

    private static String uuid(String value) {
        return UUID.fromString(value).toString();
    }

    private static byte[] sign(SecretKeySpec key, String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(bytes(value));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "cannot sign reading session value",
                    exception
            );
        }
    }

    private static byte[] derive(byte[] key, byte[] domain) {
        return sign(
                new SecretKeySpec(key, ALGORITHM),
                new String(domain, StandardCharsets.UTF_8)
        );
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(
                "reading session token is invalid or expired"
        );
    }
}
