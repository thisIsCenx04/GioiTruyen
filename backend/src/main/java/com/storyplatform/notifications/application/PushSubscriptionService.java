package com.storyplatform.notifications.application;

import com.storyplatform.notifications.application.port
        .PushSubscriptionRepository;

import java.net.URI;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class PushSubscriptionService
        implements PushSubscriptionOperations {

    private final PushSubscriptionRepository repository;
    private final Clock clock;
    private final Supplier<String> identifiers;

    public PushSubscriptionService(
            PushSubscriptionRepository repository,
            Clock clock,
            Supplier<String> identifiers
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identifiers = Objects.requireNonNull(
                identifiers,
                "identifiers"
        );
    }

    @Override
    public SubscriptionView register(
            String userId,
            SubscriptionCommand command
    ) {
        String user = uuid(userId, "userId");
        if (command == null) {
            throw invalid("Push subscription is required.");
        }
        String endpoint = endpoint(command.endpoint());
        String p256dh = key(command.p256dh(), "p256dh", 43, 128);
        String auth = key(command.auth(), "auth", 16, 64);
        return repository.save(
                uuid(identifiers.get(), "subscriptionId"),
                user,
                endpoint,
                p256dh,
                auth,
                clock.instant()
        );
    }

    @Override
    public void remove(String userId, String subscriptionId) {
        if (!repository.remove(
                uuid(subscriptionId, "subscriptionId"),
                uuid(userId, "userId"),
                clock.instant()
        )) {
            throw new NotificationException(
                    "PUSH_SUBSCRIPTION_NOT_FOUND",
                    "The push subscription was not found.",
                    NotificationException.Kind.NOT_FOUND
            );
        }
    }

    private static String endpoint(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || value.length() > 2048) {
                throw invalid("Push endpoint must be a safe HTTPS URL.");
            }
            return uri.toASCIIString();
        } catch (RuntimeException exception) {
            throw invalid("Push endpoint must be a safe HTTPS URL.");
        }
    }

    private static String key(
            String value,
            String field,
            int minimum,
            int maximum
    ) {
        if (value == null
                || value.length() < minimum
                || value.length() > maximum
                || !value.matches("[A-Za-z0-9_-]+")) {
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
                "PUSH_SUBSCRIPTION_INVALID",
                message,
                NotificationException.Kind.INVALID
        );
    }
}
