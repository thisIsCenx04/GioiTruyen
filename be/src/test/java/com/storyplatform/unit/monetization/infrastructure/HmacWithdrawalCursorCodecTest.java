package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.application.port
        .WithdrawalCursorCodec;
import com.storyplatform.monetization.infrastructure.security
        .HmacWithdrawalCursorCodec;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacWithdrawalCursorCodecTest {

    @Test
    void roundTripsAndRejectsTamperedCursor() {
        var codec = new HmacWithdrawalCursorCodec(new byte[32]);
        var position = new WithdrawalCursorCodec.Position(
                Instant.parse("2026-07-25T01:00:00Z"),
                "10000000-0000-4000-8000-000000000001"
        );
        String cursor = codec.encode(position);
        String tampered = cursor.substring(0, cursor.length() - 1)
                + (cursor.endsWith("A") ? "B" : "A");

        assertThat(codec.decode(cursor)).contains(position);
        assertThat(codec.decode(tampered)).isEmpty();
        assertThat(codec.decode("invalid")).isEmpty();
        assertThat(codec.decode(null)).isEmpty();
    }

    @Test
    void requiresStrongKeyAndValidPosition() {
        assertThatThrownBy(() ->
                new HmacWithdrawalCursorCodec(new byte[31])
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                new HmacWithdrawalCursorCodec(new byte[32]).encode(
                        new WithdrawalCursorCodec.Position(
                                Instant.EPOCH,
                                "invalid"
                        )
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
