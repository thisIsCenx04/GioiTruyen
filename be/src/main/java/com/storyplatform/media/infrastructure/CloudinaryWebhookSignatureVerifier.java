package com.storyplatform.media.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Verifies Cloudinary webhook signatures.
 *
 * <p>Cloudinary signs the request by computing
 * {@code SHA-256(body + timestamp + secret)} or
 * {@code SHA-1(body + timestamp + secret)} and including the hex digest in the
 * {@code X-Cld-Signature} header (along with the Unix timestamp in
 * {@code X-Cld-Timestamp}).
 *
 * <p>Signatures are only accepted if the timestamp is within
 * {@code ±tolerance} of the current time.
 */
public final class CloudinaryWebhookSignatureVerifier {

    private final String secret;
    private final Duration tolerance;
    private final Clock clock;

    public CloudinaryWebhookSignatureVerifier(
            String secret,
            Duration tolerance,
            Clock clock
    ) {
        this.secret = Objects.requireNonNull(secret, "secret");
        this.tolerance = Objects.requireNonNull(tolerance, "tolerance");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Verifies a Cloudinary webhook signature.
     *
     * @param body      the raw request body (may be {@code null} – will return
     *                  {@code false})
     * @param timestamp the {@code X-Cld-Timestamp} header value
     * @param signature the {@code X-Cld-Signature} header value
     * @return {@code true} if and only if the signature is valid and the
     *         timestamp is within the configured tolerance window
     */
    public boolean verify(byte[] body, String timestamp, String signature) {
        if (body == null || timestamp == null || signature == null) {
            return false;
        }
        // Parse timestamp
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            return false;
        }
        // Check within tolerance window: only accept timestamps in the past
        // (up to tolerance seconds ago) - future timestamps are always rejected
        Instant now = clock.instant();
        Instant signedAt = Instant.ofEpochSecond(ts);
        if (signedAt.isAfter(now)
                || signedAt.isBefore(now.minus(tolerance))) {
            return false;
        }
        // Validate hex encoding
        if (!signature.matches("[0-9a-f]+")) {
            return false;
        }
        // Try SHA-256 and SHA-1
        return verifyAlgorithm(body, timestamp, signature, "SHA-256")
                || verifyAlgorithm(body, timestamp, signature, "SHA-1");
    }

    private boolean verifyAlgorithm(
            byte[] body,
            String timestamp,
            String signature,
            String algorithm
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            digest.update(body);
            digest.update(timestamp.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(
                    secret.getBytes(StandardCharsets.UTF_8)
            );
            String expected = HexFormat.of().formatHex(hash);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    signature.getBytes(StandardCharsets.US_ASCII)
            );
        } catch (NoSuchAlgorithmException exception) {
            return false;
        }
    }
}
