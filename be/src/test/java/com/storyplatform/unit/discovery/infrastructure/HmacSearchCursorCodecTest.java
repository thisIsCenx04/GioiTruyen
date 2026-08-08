package com.storyplatform.unit.discovery.infrastructure;

import com.storyplatform.discovery.infrastructure.security
        .HmacSearchCursorCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacSearchCursorCodecTest {

    @Test
    void roundTripsOpaqueTokensAndRejectsTampering() {
        var codec = new HmacSearchCursorCodec(new byte[32]);
        String cursor = codec.encode("opaque-atlas-token");

        assertThat(codec.decode(cursor)).isEqualTo("opaque-atlas-token");
        assertThatThrownBy(() -> codec.decode(cursor + "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.encode(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HmacSearchCursorCodec(new byte[1]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
