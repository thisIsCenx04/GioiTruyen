package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.port.PaymentWebhookVerifier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

public final class HmacPaymentWebhookVerifier
        implements PaymentWebhookVerifier {

    private static final Duration FUTURE_SKEW = Duration.ofMinutes(5);
    private final String expectedProvider;
    private final byte[] secret;
    private final Duration maximumAge;
    private final Clock clock;

    public HmacPaymentWebhookVerifier(
            String expectedProvider,
            String secret,
            Duration maximumAge,
            Clock clock
    ) {
        if (expectedProvider == null
                || !expectedProvider.matches("[a-z0-9-]{2,32}")) {
            throw new IllegalArgumentException("Provider is invalid.");
        }
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException(
                    "Payment webhook secret must contain 32 characters."
            );
        }
        if (maximumAge == null
                || maximumAge.isZero()
                || maximumAge.isNegative()
                || maximumAge.compareTo(Duration.ofHours(2)) > 0) {
            throw new IllegalArgumentException(
                    "Payment webhook maximum age is invalid."
            );
        }
        this.expectedProvider = expectedProvider;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.maximumAge = maximumAge;
        this.clock = clock;
    }

    @Override
    public boolean verify(
            String provider,
            byte[] rawBody,
            String timestamp,
            String signature
    ) {
        if (!expectedProvider.equals(provider)
                || rawBody == null
                || timestamp == null
                || signature == null
                || !signature.matches("[0-9a-f]{64}")) {
            return false;
        }
        long epochSecond;
        try {
            epochSecond = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            return false;
        }
        Duration age = Duration.between(
                Instant.ofEpochSecond(epochSecond),
                clock.instant()
        );
        if (age.compareTo(FUTURE_SKEW.negated()) < 0
                || age.compareTo(maximumAge) > 0) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) '.');
            byte[] expected = mac.doFinal(rawBody);
            return MessageDigest.isEqual(
                    expected,
                    HexFormat.of().parseHex(signature)
            );
        } catch (GeneralSecurityException
                 | IllegalArgumentException exception) {
            return false;
        }
    }
}
