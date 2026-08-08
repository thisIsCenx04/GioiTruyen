package com.storyplatform.reading.application.port;

import java.time.Instant;

public interface ReadingHistoryCursorCodec {

    String encode(Cursor cursor);

    Cursor decode(String value);

    record Cursor(
            String userId,
            Instant updatedAt,
            String storyId
    ) {
    }
}
