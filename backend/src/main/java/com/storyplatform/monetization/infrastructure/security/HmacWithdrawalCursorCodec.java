package com.storyplatform.monetization.infrastructure.security;

import com.storyplatform.monetization.application.port
        .WithdrawalCursorCodec;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class HmacWithdrawalCursorCodec
        implements WithdrawalCursorCodec {

    private static final String ALGORITHM = "HmacSHA256";
    private static final byte[] DOMAIN =
            "gioitruyen:withdrawal-cursor:v1"
                    .getBytes(StandardCharsets.US_ASCII);
    private final SecretKeySpec key;

    public HmacWithdrawalCursorCodec(byte[] rootKey) {
        Objects.requireNonNull(rootKey, "rootKey");
        if (rootKey.length < 32) {
            throw new IllegalArgumentException(
                    "Withdrawal cursor key requires 32 bytes."
            );
        }
        key = new SecretKeySpec(derive(rootKey), ALGORITHM);
    }

    @Override
    public String encode(Position position) {
        Objects.requireNonNull(position, "position");
        String payload = "v1|" + position.createdAt() + "|"
                + UUID.fromString(position.id());
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sign(encoded));
    }

    @Override
    public Optional<Position> decode(String value) {
        if (value == null || value.length() > 512) {
            return Optional.empty();
        }
        try {
            String[] token = value.split("\\.", -1);
            if (token.length != 2 || !MessageDigest.isEqual(
                    sign(token[0]),
                    Base64.getUrlDecoder().decode(token[1])
            )) {
                return Optional.empty();
            }
            String[] payload = new String(
                    Base64.getUrlDecoder().decode(token[0]),
                    StandardCharsets.UTF_8
            ).split("\\|", -1);
            if (payload.length != 3 || !"v1".equals(payload[0])) {
                return Optional.empty();
            }
            return Optional.of(new Position(
                    Instant.parse(payload[1]),
                    UUID.fromString(payload[2]).toString()
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to sign withdrawal cursor.",
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
                    "Unable to derive withdrawal cursor key.",
                    exception
            );
        }
    }
}
