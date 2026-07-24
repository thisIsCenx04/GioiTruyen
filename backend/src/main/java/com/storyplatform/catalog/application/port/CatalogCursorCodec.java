package com.storyplatform.catalog.application.port;

import java.time.Instant;

public interface CatalogCursorCodec {

    String encode(Cursor cursor);

    Cursor decode(String value);

    record Cursor(
            StoryRepository.Sort sort,
            Instant value,
            String id
    ) {
    }
}
