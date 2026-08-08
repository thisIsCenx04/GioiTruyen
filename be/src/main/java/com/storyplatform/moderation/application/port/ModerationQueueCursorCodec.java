package com.storyplatform.moderation.application.port;

import java.time.Instant;

public interface ModerationQueueCursorCodec {

    String encode(Cursor cursor);

    Cursor decode(String value);

    record Cursor(int priority, Instant submittedAt, String id) {
    }
}
