package com.storyplatform.monetization.infrastructure.security;

import com.storyplatform.monetization.application.port.ReferralCodeCodec;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

public final class HmacReferralCodeCodec implements ReferralCodeCodec {

    private static final int TAG_BYTES = 16;
    private final byte[] key;

    public HmacReferralCodeCodec(byte[] key) {
        if (key == null || key.length < 32) {
            throw new IllegalArgumentException(
                    "Referral code key requires at least 32 bytes."
            );
        }
        this.key = key.clone();
    }

    @Override
    public String encode(String userId) {
        UUID id = UUID.fromString(userId);
        byte[] payload = ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
        byte[] signature = signature(payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                ByteBuffer.allocate(payload.length + TAG_BYTES)
                        .put(payload)
                        .put(signature, 0, TAG_BYTES)
                        .array()
        );
    }

    @Override
    public Optional<String> decode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(code);
            if (decoded.length != 16 + TAG_BYTES) {
                return Optional.empty();
            }
            byte[] payload = Arrays.copyOfRange(decoded, 0, 16);
            byte[] supplied = Arrays.copyOfRange(
                    decoded,
                    16,
                    decoded.length
            );
            byte[] expected = Arrays.copyOf(
                    signature(payload),
                    TAG_BYTES
            );
            if (!MessageDigest.isEqual(supplied, expected)) {
                return Optional.empty();
            }
            ByteBuffer bytes = ByteBuffer.wrap(payload);
            return Optional.of(new UUID(
                    bytes.getLong(),
                    bytes.getLong()
            ).toString());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Override
    public String hash(String code) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            code.getBytes(StandardCharsets.US_ASCII)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private byte[] signature(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update("gioitruyen:referral:v1\0".getBytes(
                    StandardCharsets.US_ASCII
            ));
            return mac.doFinal(payload);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to sign referral code.",
                    exception
            );
        }
    }
}
