package com.storyplatform.unit.notifications.infrastructure;

import com.storyplatform.notifications.application.port
        .NotificationCursorCodec;
import com.storyplatform.notifications.infrastructure.security
        .HmacNotificationCursorCodec;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacNotificationCursorCodecTest {

    @Test
    void roundTripsAndRejectsTamperingOrWrongKey() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 7);
        var codec = new HmacNotificationCursorCodec(key);
        var position = new NotificationCursorCodec.Position(
                "10000000-0000-4000-8000-000000000001",
                Instant.parse("2026-07-24T00:00:00Z"),
                "20000000-0000-4000-8000-000000000001"
        );
        String encoded = codec.encode(position);

        assertThat(codec.decode(encoded)).isEqualTo(position);
        assertThatThrownBy(() -> codec.decode(encoded + "x"))
                .isInstanceOf(IllegalArgumentException.class);
        byte[] other = new byte[32];
        Arrays.fill(other, (byte) 8);
        assertThatThrownBy(() ->
                new HmacNotificationCursorCodec(other).decode(encoded))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesKeyAndMalformedInputs() {
        assertThatThrownBy(() ->
                new HmacNotificationCursorCodec(new byte[31]))
                .isInstanceOf(IllegalArgumentException.class);
        var codec = new HmacNotificationCursorCodec(new byte[32]);
        assertThatThrownBy(() -> codec.decode("bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
