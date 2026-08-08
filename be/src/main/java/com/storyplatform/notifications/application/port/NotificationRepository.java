package com.storyplatform.notifications.application.port;

import com.storyplatform.notifications.application.NotificationOperations;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface NotificationRepository {

    Slice list(
            String recipientId,
            int limit,
            NotificationCursorCodec.Position after
    );

    Optional<NotificationOperations.NotificationView> markRead(
            String recipientId,
            String notificationId,
            Instant now
    );

    Instant markAllRead(String recipientId, Instant now);

    long unreadCount(String recipientId);

    SaveResult saveIfAbsent(
            String id,
            String recipientId,
            String eventKey,
            String type,
            String title,
            String body,
            Map<String, String> data,
            Instant createdAt
    );

    record Slice(
            List<NotificationOperations.NotificationView> items,
            boolean hasMore
    ) {
        public Slice {
            items = List.copyOf(items);
        }
    }

    record SaveResult(
            NotificationOperations.NotificationView notification,
            boolean created
    ) {
    }
}
