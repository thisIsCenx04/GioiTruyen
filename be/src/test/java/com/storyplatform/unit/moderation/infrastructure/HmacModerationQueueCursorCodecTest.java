package com.storyplatform.unit.moderation.infrastructure;

import com.storyplatform.moderation.application.port
        .ModerationQueueCursorCodec;
import com.storyplatform.moderation.infrastructure.security
        .HmacModerationQueueCursorCodec;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacModerationQueueCursorCodecTest {

    private final HmacModerationQueueCursorCodec codec =
            new HmacModerationQueueCursorCodec(
                    "a-secure-moderation-cursor-root-key"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

    @Test
    void roundTripsPriorityTimeAndIdentifier() {
        var cursor = new ModerationQueueCursorCodec.Cursor(
                90,
                Instant.parse("2026-07-24T00:00:00Z"),
                "80000000-0000-4000-8000-000000000001"
        );

        assertThat(codec.decode(codec.encode(cursor))).isEqualTo(cursor);
    }

    @Test
    void rejectsTamperedMalformedAndOversizedTokens() {
        String valid = codec.encode(new ModerationQueueCursorCodec.Cursor(
                70,
                Instant.parse("2026-07-24T00:00:00Z"),
                "80000000-0000-4000-8000-000000000001"
        ));
        assertThatThrownBy(() -> codec.decode(valid + "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("x".repeat(513)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.encode(
                new ModerationQueueCursorCodec.Cursor(
                        -1,
                        Instant.parse("2026-07-24T00:00:00Z"),
                        "80000000-0000-4000-8000-000000000001"
                )
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.encode(
                new ModerationQueueCursorCodec.Cursor(
                        101,
                        Instant.parse("2026-07-24T00:00:00Z"),
                        "80000000-0000-4000-8000-000000000001"
                )
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HmacModerationQueueCursorCodec(
                new byte[31]
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
