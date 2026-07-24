package com.storyplatform.unit.catalog.infrastructure;

import com.storyplatform.catalog.application.port.CatalogCursorCodec;
import com.storyplatform.catalog.application.port.StoryRepository;
import com.storyplatform.catalog.infrastructure.security
        .HmacCatalogCursorCodec;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class HmacCatalogCursorCodecTest {

    private final HmacCatalogCursorCodec codec =
            new HmacCatalogCursorCodec(new byte[32]);

    @Test
    void roundTripsAVersionedOpaqueCursor() {
        var cursor = new CatalogCursorCodec.Cursor(
                StoryRepository.Sort.PUBLISHED_DESC,
                Instant.parse("2026-07-24T00:00:00Z"),
                "20000000-0000-4000-8000-000000000001"
        );

        assertThat(codec.decode(codec.encode(cursor))).isEqualTo(cursor);
    }

    @Test
    void rejectsTamperingAndOversizedInput() {
        var cursor = new CatalogCursorCodec.Cursor(
                StoryRepository.Sort.UPDATED_DESC,
                Instant.EPOCH,
                "20000000-0000-4000-8000-000000000001"
        );
        String encoded = codec.encode(cursor);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> codec.decode(encoded + "x"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> codec.decode("x".repeat(513)));
    }
}
