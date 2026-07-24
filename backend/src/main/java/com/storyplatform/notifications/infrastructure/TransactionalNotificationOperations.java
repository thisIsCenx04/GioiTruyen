package com.storyplatform.notifications.infrastructure;

import com.storyplatform.notifications.application.NotificationOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

public class TransactionalNotificationOperations
        implements NotificationOperations {

    private final NotificationOperations delegate;

    public TransactionalNotificationOperations(NotificationOperations delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPage list(
            String recipientId,
            int limit,
            String cursor
    ) {
        return delegate.list(recipientId, limit, cursor);
    }

    @Override
    @Transactional
    public NotificationView markRead(
            String recipientId,
            String notificationId
    ) {
        return delegate.markRead(recipientId, notificationId);
    }

    @Override
    @Transactional
    public ReadWatermark markAllRead(String recipientId) {
        return delegate.markAllRead(recipientId);
    }

    @Override
    @Transactional
    public NotificationView append(
            String recipientId,
            String eventKey,
            String type,
            String title,
            String body,
            Map<String, String> data
    ) {
        return delegate.append(
                recipientId,
                eventKey,
                type,
                title,
                body,
                data
        );
    }
}
