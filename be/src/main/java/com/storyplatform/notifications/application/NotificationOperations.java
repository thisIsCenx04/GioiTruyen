package com.storyplatform.notifications.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface NotificationOperations {

    NotificationPage list(String recipientId, int limit, String cursor);

    NotificationView markRead(String recipientId, String notificationId);

    ReadWatermark markAllRead(String recipientId);

    NotificationView append(
            String recipientId,
            String eventKey,
            String type,
            String title,
            String body,
            Map<String, String> data
    );

    record NotificationView(
            String id,
            String type,
            String title,
            String body,
            Map<String, String> data,
            Instant readAt,
            Instant createdAt
    ) {
        public NotificationView {
            data = Map.copyOf(data);
        }
    }

    record NotificationPage(
            List<NotificationView> items,
            String nextCursor,
            boolean hasMore,
            long unreadCount
    ) {
        public NotificationPage {
            items = List.copyOf(items);
        }
    }

    record ReadWatermark(Instant readBefore, long unreadCount) {
    }
}
