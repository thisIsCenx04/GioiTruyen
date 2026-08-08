package com.storyplatform.notifications.application;

import com.storyplatform.notifications.application.port
        .NotificationPreferenceRepository;
import com.storyplatform.notifications.application.port
        .UnsubscribeTokenCodec;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class NotificationPreferenceService
        implements NotificationPreferenceOperations {

    public static final String CONSENT_VERSION = "notifications-2026.1";
    private static final Set<String> CATEGORIES = Set.of(
            "STORY_UPDATES",
            "COMMUNITY",
            "MODERATION",
            "ACCOUNT"
    );
    private final NotificationPreferenceRepository repository;
    private final UnsubscribeTokenCodec unsubscribeTokens;
    private final Clock clock;

    public NotificationPreferenceService(
            NotificationPreferenceRepository repository,
            UnsubscribeTokenCodec unsubscribeTokens,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.unsubscribeTokens = Objects.requireNonNull(
                unsubscribeTokens,
                "unsubscribeTokens"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PreferenceView get(String userId) {
        String user = uuid(userId);
        return repository.find(user).orElseGet(() -> defaults(user));
    }

    @Override
    public PreferenceView update(
            String userId,
            long expectedVersion,
            PreferenceCommand command
    ) {
        String user = uuid(userId);
        if (expectedVersion < 0 || command == null
                || !CATEGORIES.containsAll(command.categories())) {
            throw invalid("Preference version or categories are invalid.");
        }
        if ((command.emailEnabled() || command.pushEnabled())
                && !command.consentGranted()) {
            throw invalid("Explicit consent is required for delivery.");
        }
        Instant now = clock.instant();
        PreferenceView preference = new PreferenceView(
                user,
                command.emailEnabled(),
                command.pushEnabled(),
                command.categories(),
                CONSENT_VERSION,
                command.consentGranted() ? now : null,
                now,
                expectedVersion + 1
        );
        if (repository.save(preference, expectedVersion)
                != NotificationPreferenceRepository.SaveOutcome.SUCCESS) {
            throw conflict();
        }
        return preference;
    }

    @Override
    public PreferenceView unsubscribe(String token) {
        UnsubscribeTokenCodec.Grant grant;
        try {
            grant = unsubscribeTokens.decode(token);
        } catch (RuntimeException exception) {
            throw invalid("Unsubscribe token is invalid.");
        }
        if (clock.instant().isAfter(grant.expiresAt())) {
            throw invalid("Unsubscribe token has expired.");
        }
        if (repository.disable(
                grant.userId(),
                grant.channel(),
                clock.instant()
        ) == NotificationPreferenceRepository.SaveOutcome.NOT_FOUND) {
            return defaults(grant.userId());
        }
        return repository.find(grant.userId())
                .orElseGet(() -> defaults(grant.userId()));
    }

    private PreferenceView defaults(String userId) {
        return new PreferenceView(
                userId,
                false,
                false,
                CATEGORIES,
                CONSENT_VERSION,
                null,
                clock.instant(),
                0
        );
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw invalid("userId must be a UUID.");
        }
    }

    private static NotificationException invalid(String message) {
        return new NotificationException(
                "NOTIFICATION_PREFERENCE_INVALID",
                message,
                NotificationException.Kind.INVALID
        );
    }

    private static NotificationException conflict() {
        return new NotificationException(
                "NOTIFICATION_PREFERENCE_CONFLICT",
                "Notification preferences changed. Reload and try again.",
                NotificationException.Kind.CONFLICT
        );
    }
}
