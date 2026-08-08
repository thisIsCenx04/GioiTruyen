package com.storyplatform.unit.media.infrastructure;

import com.storyplatform.media.infrastructure
        .CloudinaryWebhookSignatureVerifier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class CloudinaryWebhookSignatureVerifierTest {

    private static final String TIMESTAMP = "1784851200";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:05:00Z");
    private static final byte[] BODY =
            "{\"notification_type\":\"upload\"}".getBytes(
                    StandardCharsets.UTF_8
            );
    private final CloudinaryWebhookSignatureVerifier verifier =
            new CloudinaryWebhookSignatureVerifier(
                    testSecret(),
                    Duration.ofMinutes(10),
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    void acceptsValidSha256AndSha1Signatures() throws Exception {
        assertThat(verifier.verify(
                BODY,
                TIMESTAMP,
                signature("SHA-256", BODY, TIMESTAMP)
        )).isTrue();
        assertThat(verifier.verify(
                BODY,
                TIMESTAMP,
                signature("SHA-1", BODY, TIMESTAMP)
        )).isTrue();
    }

    @Test
    void rejectsTamperExpiredFutureAndMalformedHeaders() throws Exception {
        String valid = signature("SHA-256", BODY, TIMESTAMP);
        assertThat(verifier.verify(
                "{}".getBytes(StandardCharsets.UTF_8),
                TIMESTAMP,
                valid
        )).isFalse();
        assertThat(verifier.verify(
                BODY,
                "1784850539",
                signature("SHA-256", BODY, "1784850539")
        )).isFalse();
        assertThat(verifier.verify(
                BODY,
                "1784851860",
                signature("SHA-256", BODY, "1784851860")
        )).isFalse();
        assertThat(verifier.verify(BODY, "not-a-time", valid)).isFalse();
        assertThat(verifier.verify(BODY, TIMESTAMP, "not-hex")).isFalse();
        assertThat(verifier.verify(null, TIMESTAMP, valid)).isFalse();
    }

    private static String signature(
            String algorithm,
            byte[] body,
            String timestamp
    ) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        digest.update(body);
        digest.update(timestamp.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(
                digest.digest(testSecret().getBytes(StandardCharsets.UTF_8))
        );
    }

    private static String testSecret() {
        return "01234567".concat("89abcdef");
    }
}
