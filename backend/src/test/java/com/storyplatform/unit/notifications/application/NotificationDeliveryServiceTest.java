package com.storyplatform.unit.notifications.application;

import com.storyplatform.notifications.application
        .NotificationDeliveryService;
import com.storyplatform.notifications.application
        .NotificationProviderException;
import com.storyplatform.notifications.application.port
        .NotificationDeliveryProvider;
import com.storyplatform.notifications.application.port
        .NotificationDeliveryRepository;
import com.storyplatform.notifications.application.port
        .NotificationPreferenceRepository;
import com.storyplatform.notifications.application.port
        .UnsubscribeTokenCodec;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDeliveryServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final NotificationDeliveryRepository repository =
            mock(NotificationDeliveryRepository.class);
    private final NotificationDeliveryProvider provider =
            mock(NotificationDeliveryProvider.class);
    private final UnsubscribeTokenCodec tokens =
            mock(UnsubscribeTokenCodec.class);

    @Test
    void sendsConsentAwareDeliveryAndCompletesLease() {
        var job = job(1);
        var target = new NotificationDeliveryRepository.DeliveryTarget(
                "reader@example.com"
        );
        when(repository.claim(any(), any(), any(), any(Integer.class)))
                .thenReturn(Optional.of(job));
        when(repository.target(job)).thenReturn(Optional.of(target));
        when(tokens.encode(any())).thenReturn("unsubscribe");
        when(provider.send(job, target, "unsubscribe"))
                .thenReturn("provider-id");

        assertThat(service(provider).processNext("worker_123")).isTrue();
        verify(repository).complete(
                job, "worker_123", "provider-id", NOW
        );
        verify(tokens).encode(new UnsubscribeTokenCodec.Grant(
                job.recipientId(),
                NotificationPreferenceRepository.Channel.EMAIL,
                NOW.plus(Duration.ofDays(90))
        ));
    }

    @Test
    void suppressesWithoutConsentOrTarget() {
        var job = job(1);
        when(repository.claim(any(), any(), any(), any(Integer.class)))
                .thenReturn(Optional.of(job));
        when(repository.target(job)).thenReturn(Optional.empty());

        service(provider).processNext("worker_123");

        verify(repository).suppress(
                job,
                "worker_123",
                "CONSENT_OR_TARGET_MISSING",
                NOW
        );
    }

    @Test
    void retriesTransientFailureAndDeadLettersPermanentOrExhausted() {
        var transientJob = job(2);
        when(repository.claim(any(), any(), any(), any(Integer.class)))
                .thenReturn(Optional.of(transientJob));
        when(repository.target(any())).thenReturn(Optional.of(
                new NotificationDeliveryRepository.DeliveryTarget("target")
        ));
        when(tokens.encode(any())).thenReturn("token");
        when(provider.send(any(), any(), any())).thenThrow(
                new NotificationProviderException(
                        "PROVIDER_503", "retry", true
                )
        );

        service(provider).processNext("worker_123");
        verify(repository).reschedule(
                transientJob,
                "worker_123",
                "PROVIDER_503",
                NOW.plusSeconds(60),
                false,
                NOW
        );

        org.mockito.Mockito.reset(repository, provider);
        var permanent = job(1);
        when(repository.claim(any(), any(), any(), any(Integer.class)))
                .thenReturn(Optional.of(permanent));
        when(repository.target(permanent)).thenReturn(Optional.of(
                new NotificationDeliveryRepository.DeliveryTarget("target")
        ));
        when(provider.send(any(), any(), any())).thenThrow(
                new NotificationProviderException(
                        "PROVIDER_400", "permanent", false
                )
        );
        service(provider).processNext("worker_123");
        verify(repository).reschedule(
                permanent,
                "worker_123",
                "PROVIDER_400",
                NOW.plusSeconds(30),
                true,
                NOW
        );
    }

    @Test
    void returnsFalseWhenQueueIsEmpty() {
        when(repository.claim(any(), any(), any(), any(Integer.class)))
                .thenReturn(Optional.empty());
        assertThat(service(provider).processNext("worker_123")).isFalse();
    }

    private NotificationDeliveryService service(
            NotificationDeliveryProvider selectedProvider
    ) {
        return new NotificationDeliveryService(
                repository,
                Map.of(
                        NotificationDeliveryRepository.Channel.EMAIL,
                        selectedProvider
                ),
                tokens,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(1),
                Duration.ofSeconds(30),
                Duration.ofDays(90),
                5
        );
    }

    private static NotificationDeliveryRepository.DeliveryJob job(
            int attempt
    ) {
        return new NotificationDeliveryRepository.DeliveryJob(
                "delivery",
                "notification",
                "10000000-0000-4000-8000-000000000001",
                NotificationDeliveryRepository.Channel.EMAIL,
                "STORY_UPDATES",
                "STORY_PUBLISHED",
                "Title",
                "Body",
                Map.of(),
                attempt
        );
    }
}
