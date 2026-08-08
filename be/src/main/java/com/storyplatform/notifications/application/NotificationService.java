package com.storyplatform.notifications.application;

import com.storyplatform.notifications.application.port
        .NotificationCursorCodec;
import com.storyplatform.notifications.application.port
        .NotificationRepository;

import java.text.Normalizer;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class NotificationService implements NotificationOperations {

    private final NotificationRepository repository;
    private final NotificationCursorCodec cursors;
    private final Clock clock;
    private final Supplier<String> identifiers;

    public NotificationService(
            NotificationRepository repository,
            NotificationCursorCodec cursors,
            Clock clock,
            Supplier<String> identifiers
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.cursors = Objects.requireNonNull(cursors, "cursors");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identifiers = Objects.requireNonNull(
                identifiers,
                "identifiers"
        );
    }

    @Override
    public NotificationPage list(
            String recipientId,
            int limit,
            String cursor
    ) {
        String recipient = uuid(recipientId, "recipientId");
        if (limit < 1 || limit > 100) {
            throw invalid("limit must be between 1 and 100.");
        }
        NotificationCursorCodec.Position position = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                position = cursors.decode(cursor);
                if (!recipient.equals(position.recipientId())) {
                    throw invalid("cursor is invalid.");
                }
            } catch (RuntimeException exception) {
                throw invalid("cursor is invalid.");
            }
        }
        NotificationRepository.Slice slice = repository.list(
                recipient,
                limit,
                position
        );
        String next = null;
        if (slice.hasMore() && !slice.items().isEmpty()) {
            NotificationView last = slice.items()
                    .get(slice.items().size() - 1);
            next = cursors.encode(new NotificationCursorCodec.Position(
                    recipient,
                    last.createdAt(),
                    last.id()
            ));
        }
        return new NotificationPage(
                slice.items(),
                next,
                slice.hasMore(),
                repository.unreadCount(recipient)
        );
    }

    @Override
    public NotificationView markRead(
            String recipientId,
            String notificationId
    ) {
        String recipient = uuid(recipientId, "recipientId");
        String notification = uuid(notificationId, "notificationId");
        return repository.markRead(
                recipient,
                notification,
                clock.instant()
        ).orElseThrow(() -> new NotificationException(
                "NOTIFICATION_NOT_FOUND",
                "The notification was not found.",
                NotificationException.Kind.NOT_FOUND
        ));
    }

    @Override
    public ReadWatermark markAllRead(String recipientId) {
        String recipient = uuid(recipientId, "recipientId");
        return new ReadWatermark(
                repository.markAllRead(recipient, clock.instant()),
                repository.unreadCount(recipient)
        );
    }

    @Override
    public NotificationView append(
            String recipientId,
            String eventKey,
            String type,
            String title,
            String body,
            Map<String, String> data
    ) {
        String recipient = uuid(recipientId, "recipientId");
        String safeEvent = token(eventKey, "eventKey");
        String safeType = code(type);
        String safeTitle = text(title, 160, true);
        String safeBody = text(body, 1000, false);
        Map<String, String> safeData = data(data);
        return repository.saveIfAbsent(
                uuid(identifiers.get(), "notificationId"),
                recipient,
                safeEvent,
                safeType,
                safeTitle,
                safeBody,
                safeData,
                clock.instant()
        ).notification();
    }

    private static Map<String, String> data(Map<String, String> values) {
        Map<String, String> source = values == null ? Map.of() : values;
        if (source.size() > 20) {
            throw invalid("data has too many fields.");
        }
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(
                token(key, "data key"),
                text(value, 500, false)
        ));
        return Map.copyOf(result);
    }

    private static String text(
            String value,
            int maximum,
            boolean required
    ) {
        String normalized = value == null
                ? ""
                : Normalizer.normalize(value, Normalizer.Form.NFC)
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if ((required && normalized.isBlank())
                || normalized.length() > maximum) {
            throw invalid("notification text is invalid.");
        }
        return normalized;
    }

    private static String code(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{2,63}")) {
            throw invalid("type is invalid.");
        }
        return value;
    }

    private static String token(String value, String field) {
        if (value == null
                || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw invalid(field + " is invalid.");
        }
        return value;
    }

    private static String uuid(String value, String field) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw invalid(field + " must be a UUID.");
        }
    }

    private static NotificationException invalid(String message) {
        return new NotificationException(
                "NOTIFICATION_INVALID",
                message,
                NotificationException.Kind.INVALID
        );
    }
}
