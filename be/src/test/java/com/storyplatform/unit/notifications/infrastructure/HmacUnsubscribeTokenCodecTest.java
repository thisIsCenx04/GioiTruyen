package com.storyplatform.unit.notifications.infrastructure;

import com.storyplatform.notifications.application.port
        .NotificationPreferenceRepository;
import com.storyplatform.notifications.application.port
        .UnsubscribeTokenCodec;
import com.storyplatform.notifications.infrastructure.security
        .HmacUnsubscribeTokenCodec;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacUnsubscribeTokenCodecTest {

    @Test
    void roundTripsAndRejectsTampering() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 3);
        var codec = new HmacUnsubscribeTokenCodec(key);
        var grant = new UnsubscribeTokenCodec.Grant(
                "10000000-0000-4000-8000-000000000001",
                NotificationPreferenceRepository.Channel.EMAIL,
                Instant.parse("2026-08-24T00:00:00Z")
        );
        String token = codec.encode(grant);

        assertThat(codec.decode(token)).isEqualTo(grant);
        assertThatThrownBy(() -> codec.decode(token + "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                new HmacUnsubscribeTokenCodec(new byte[31]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
