package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.infrastructure
        .HmacPaymentWebhookVerifier;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class HmacPaymentWebhookVerifierTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T00:00:00Z");
    private static final String SECRET = "s".repeat(32);
    private static final byte[] BODY = "{}".getBytes(StandardCharsets.UTF_8);
    private final HmacPaymentWebhookVerifier verifier =
            new HmacPaymentWebhookVerifier(
                    "local-bank",
                    SECRET,
                    Duration.ofMinutes(10),
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    void verifiesRawBodyProviderAndFreshTimestamp() throws Exception {
        String timestamp = Long.toString(NOW.getEpochSecond());

        assertThat(verifier.verify(
                "local-bank",
                BODY,
                timestamp,
                sign(timestamp, BODY)
        )).isTrue();
        assertThat(verifier.verify(
                "other-bank",
                BODY,
                timestamp,
                sign(timestamp, BODY)
        )).isFalse();
    }

    @Test
    void rejectsTamperingMalformedAndReplayTimestamps() throws Exception {
        String timestamp = Long.toString(NOW.getEpochSecond());
        assertThat(verifier.verify(
                "local-bank",
                "tampered".getBytes(StandardCharsets.UTF_8),
                timestamp,
                sign(timestamp, BODY)
        )).isFalse();
        assertThat(verifier.verify(
                "local-bank",
                BODY,
                "invalid",
                "x"
        )).isFalse();
        String old = Long.toString(NOW.minusSeconds(601).getEpochSecond());
        assertThat(verifier.verify(
                "local-bank",
                BODY,
                old,
                sign(old, BODY)
        )).isFalse();
    }

    private static String sign(
            String timestamp,
            byte[] body
    ) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        ));
        mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
        mac.update((byte) '.');
        return HexFormat.of().formatHex(mac.doFinal(body));
    }
}
