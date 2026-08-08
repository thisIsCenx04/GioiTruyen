package com.storyplatform.notifications.application.port;

import java.time.Instant;

public interface NotificationCursorCodec {

    String encode(Position position);

    Position decode(String cursor);

    record Position(String recipientId, Instant createdAt, String id) {
    }
}
