package com.storyplatform.unit.reading.infrastructure;

import com.storyplatform.reading.application.port.ReadingSessionTokenCodec;
import com.storyplatform.reading.infrastructure.security
        .HmacReadingSessionTokenCodec;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacReadingSessionTokenCodecTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final HmacReadingSessionTokenCodec codec =
            new HmacReadingSessionTokenCodec(new byte[32]);

    @Test
    void verifiesAnUnexpiredTokenAndRejectsForgeryAndExpiry() {
        String actorRef = codec.fingerprint("anonymous:install:address");
        var claims = new ReadingSessionTokenCodec.Claims(
                "40000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                "30000000-0000-4000-8000-000000000001",
                actorRef,
                NOW,
                NOW.plusSeconds(60)
        );
        String token = codec.issue(claims);

        assertThat(codec.verify(token, NOW.plusSeconds(59)))
                .isEqualTo(claims);
        assertThatThrownBy(() -> codec.verify(token + "x", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.verify(
                token,
                NOW.plusSeconds(60)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void boundsSecretsSubjectsAndTokenLifetime() {
        assertThat(codec.fingerprint("reader"))
                .matches("[A-Za-z0-9_-]{43}");
        assertThatThrownBy(() -> codec.fingerprint(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HmacReadingSessionTokenCodec(
                new byte[31]
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.issue(
                new ReadingSessionTokenCodec.Claims(
                        "40000000-0000-4000-8000-000000000001",
                        "20000000-0000-4000-8000-000000000001",
                        "30000000-0000-4000-8000-000000000001",
                        codec.fingerprint("reader"),
                        NOW,
                        NOW
                )
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
