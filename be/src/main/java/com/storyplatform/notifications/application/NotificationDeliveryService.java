package com.storyplatform.notifications.application;

import com.storyplatform.notifications.application.port
        .NotificationDeliveryProvider;
import com.storyplatform.notifications.application.port
        .NotificationDeliveryRepository;
import com.storyplatform.notifications.application.port
        .NotificationPreferenceRepository;
import com.storyplatform.notifications.application.port
        .UnsubscribeTokenCodec;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public final class NotificationDeliveryService
        implements NotificationDeliveryOperations {

    private final NotificationDeliveryRepository repository;
    private final Map<NotificationDeliveryRepository.Channel,
            NotificationDeliveryProvider> providers;
    private final UnsubscribeTokenCodec unsubscribeTokens;
    private final Clock clock;
    private final Duration lease;
    private final Duration baseRetry;
    private final Duration unsubscribeTtl;
    private final int maximumAttempts;

    public NotificationDeliveryService(
            NotificationDeliveryRepository repository,
            Map<NotificationDeliveryRepository.Channel,
                    NotificationDeliveryProvider> providers,
            UnsubscribeTokenCodec unsubscribeTokens,
            Clock clock,
            Duration lease,
            Duration baseRetry,
            Duration unsubscribeTtl,
            int maximumAttempts
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.providers = Map.copyOf(providers);
        this.unsubscribeTokens = Objects.requireNonNull(
                unsubscribeTokens,
                "unsubscribeTokens"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lease = positive(lease, "lease");
        this.baseRetry = positive(baseRetry, "baseRetry");
        this.unsubscribeTtl = positive(unsubscribeTtl, "unsubscribeTtl");
        if (maximumAttempts < 1 || maximumAttempts > 20) {
            throw new IllegalArgumentException(
                    "maximumAttempts must be between 1 and 20"
            );
        }
        this.maximumAttempts = maximumAttempts;
    }

    @Override
    public boolean processNext(String workerId) {
        if (workerId == null
                || !workerId.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new IllegalArgumentException("workerId is invalid");
        }
        var now = clock.instant();
        var claimed = repository.claim(
                workerId,
                now,
                now.plus(lease),
                maximumAttempts
        );
        if (claimed.isEmpty()) {
            return false;
        }
        var job = claimed.orElseThrow();
        var target = repository.target(job);
        if (target.isEmpty()) {
            repository.suppress(
                    job,
                    workerId,
                    "CONSENT_OR_TARGET_MISSING",
                    clock.instant()
            );
            return true;
        }
        try {
            NotificationDeliveryProvider provider = providers.get(
                    job.channel()
            );
            if (provider == null) {
                throw new NotificationProviderException(
                        "PROVIDER_NOT_CONFIGURED",
                        "Notification provider is not configured.",
                        false
                );
            }
            String unsubscribe = unsubscribeTokens.encode(
                    new UnsubscribeTokenCodec.Grant(
                            job.recipientId(),
                            NotificationPreferenceRepository.Channel.valueOf(
                                    job.channel().name()
                            ),
                            clock.instant().plus(unsubscribeTtl)
                    )
            );
            repository.complete(
                    job,
                    workerId,
                    provider.send(job, target.orElseThrow(), unsubscribe),
                    clock.instant()
            );
        } catch (RuntimeException exception) {
            boolean retryable = !(exception
                    instanceof NotificationProviderException provider)
                    || provider.retryable();
            String code = exception instanceof NotificationProviderException
                    provider ? provider.code() : "PROVIDER_FAILED";
            boolean dead = !retryable || job.attempt() >= maximumAttempts;
            repository.reschedule(
                    job,
                    workerId,
                    code,
                    clock.instant().plus(backoff(job.attempt())),
                    dead,
                    clock.instant()
            );
        }
        return true;
    }

    private Duration backoff(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 10);
        Duration delay = baseRetry.multipliedBy(multiplier);
        return delay.compareTo(Duration.ofHours(6)) > 0
                ? Duration.ofHours(6)
                : delay;
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
