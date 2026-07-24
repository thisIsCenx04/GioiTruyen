package com.storyplatform.unit.reading.infrastructure;

import com.storyplatform.reading.application.port
        .ReadingHistoryCursorCodec;
import com.storyplatform.reading.infrastructure.security
        .HmacReadingHistoryCursorCodec;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacReadingHistoryCursorCodecTest {

    @Test
    void roundTripsAndRejectsTampering() {
        var codec = new HmacReadingHistoryCursorCodec(
                "a".repeat(32).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        var cursor = new ReadingHistoryCursorCodec.Cursor(
                "10000000-0000-4000-8000-000000000001",
                Instant.parse("2026-07-24T00:00:00Z"),
                "20000000-0000-4000-8000-000000000001"
        );
        String encoded = codec.encode(cursor);

        assertThat(codec.decode(encoded)).isEqualTo(cursor);
        assertThatThrownBy(() -> codec.decode(encoded + "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("a".repeat(513)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.encode(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiresAProperlySizedRootKey() {
        assertThatThrownBy(() -> new HmacReadingHistoryCursorCodec(
                new byte[31]
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HmacReadingHistoryCursorCodec(null))
                .isInstanceOf(NullPointerException.class);
    }
}
