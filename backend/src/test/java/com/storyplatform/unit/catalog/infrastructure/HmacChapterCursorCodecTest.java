package com.storyplatform.unit.catalog.infrastructure;

import com.storyplatform.catalog.application.port.ChapterCursorCodec;
import com.storyplatform.catalog.infrastructure.security
        .HmacChapterCursorCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacChapterCursorCodecTest {

    private final HmacChapterCursorCodec codec =
            new HmacChapterCursorCodec(new byte[32]);

    @Test
    void roundTripsAStoryBoundCursorAndRejectsTampering() {
        var cursor = new ChapterCursorCodec.Cursor(
                "10000000-0000-4000-8000-000000000001",
                21,
                "20000000-0000-4000-8000-000000000001"
        );
        String encoded = codec.encode(cursor);

        assertThat(codec.decode(encoded)).isEqualTo(cursor);
        assertThatThrownBy(() -> codec.decode(encoded + "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWeakKeysAndMalformedTokens() {
        assertThatThrownBy(() -> new HmacChapterCursorCodec(new byte[31]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("not-a-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
