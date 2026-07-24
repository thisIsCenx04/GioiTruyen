package com.storyplatform.media.infrastructure;

import com.storyplatform.media.application.port.WebhookSignatureVerifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

public final class CloudinaryWebhookSignatureVerifier
        implements WebhookSignatureVerifier {

    private static final Duration MAXIMUM_FUTURE_SKEW =
            Duration.ofMinutes(5);

    private final byte[] secret;
    private final Duration maximumAge;
    private final Clock clock;

    public CloudinaryWebhookSignatureVerifier(
            String secret,
            Duration maximumAge,
            Clock clock
    ) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalArgumentException(
                    "webhook secret must contain at least 16 characters"
            );
        }
        if (maximumAge == null
                || maximumAge.isNegative()
                || maximumAge.isZero()
                || maximumAge.compareTo(Duration.ofHours(2)) > 0) {
            throw new IllegalArgumentException(
                    "webhook maximum age must be between 1 second and 2 hours"
            );
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.maximumAge = maximumAge;
        this.clock = clock;
    }

    @Override
    public boolean verify(
            byte[] body,
            String timestamp,
            String signature
    ) {
        if (body == null
                || timestamp == null
                || signature == null
                || !signature.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) {
            return false;
        }
        long epochSecond;
        try {
            epochSecond = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            return false;
        }
        Instant signedAt = Instant.ofEpochSecond(epochSecond);
        Duration age = Duration.between(signedAt, clock.instant());
        if (age.compareTo(MAXIMUM_FUTURE_SKEW.negated()) < 0
                || age.compareTo(maximumAge) > 0) {
            return false;
        }
        byte[] expected = digest(
                signature.length() == 64 ? "SHA-256" : "SHA-1",
                body,
                timestamp
        );
        byte[] supplied;
        try {
            supplied = HexFormat.of().parseHex(signature);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return MessageDigest.isEqual(expected, supplied);
    }

    private byte[] digest(
            String algorithm,
            byte[] body,
            String timestamp
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            digest.update(body);
            digest.update(timestamp.getBytes(StandardCharsets.UTF_8));
            return digest.digest(secret);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    algorithm + " is unavailable",
                    exception
            );
        }
    }
}
