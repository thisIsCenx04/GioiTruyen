package com.storyplatform.notifications.infrastructure.security;

import com.storyplatform.notifications.application.port
        .NotificationPreferenceRepository;
import com.storyplatform.notifications.application.port
        .UnsubscribeTokenCodec;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public final class HmacUnsubscribeTokenCodec
        implements UnsubscribeTokenCodec {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final byte[] DOMAIN =
            "gioitruyen:notification-unsubscribe:v1"
                    .getBytes(StandardCharsets.UTF_8);
    private final SecretKeySpec key;

    public HmacUnsubscribeTokenCodec(byte[] rootKey) {
        Objects.requireNonNull(rootKey, "rootKey");
        if (rootKey.length < 32) {
            throw new IllegalArgumentException(
                    "unsubscribe token key requires 32 bytes"
            );
        }
        key = new SecretKeySpec(derive(rootKey), ALGORITHM);
    }

    @Override
    public String encode(Grant grant) {
        Objects.requireNonNull(grant, "grant");
        String payload = String.join(
                "|",
                "v1",
                UUID.fromString(grant.userId()).toString(),
                grant.channel().name(),
                Long.toString(grant.expiresAt().getEpochSecond())
        );
        String encoded = ENCODER.encodeToString(
                payload.getBytes(StandardCharsets.UTF_8)
        );
        return encoded + "." + ENCODER.encodeToString(sign(encoded));
    }

    @Override
    public Grant decode(String value) {
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
            return new Grant(
                    UUID.fromString(payload[1]).toString(),
                    NotificationPreferenceRepository.Channel.valueOf(
                            payload[2]
                    ),
                    Instant.ofEpochSecond(Long.parseLong(payload[3]))
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
                    "cannot sign unsubscribe token",
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
                    "cannot derive unsubscribe token key",
                    exception
            );
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(
                "unsubscribe token is invalid"
        );
    }
}
