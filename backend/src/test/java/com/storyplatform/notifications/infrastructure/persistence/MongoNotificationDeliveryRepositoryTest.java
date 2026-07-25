package com.storyplatform.notifications.infrastructure.persistence;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.notifications.application.port
        .NotificationDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class MongoNotificationDeliveryRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void enqueuesBothChannelsAndMapsAllCategories() {
        MongoTemplate mongo = mock(MongoTemplate.class);

        for (String type : new String[]{
                "COMMENT_CREATED",
                "REACTION_ADDED",
                "MODERATION_DECIDED",
                "COPYRIGHT_RECEIVED",
                "ACCOUNT_CHANGED",
                "SECURITY_ALERT",
                "STORY_PUBLISHED"
        }) {
            MongoNotificationDeliveryRepository.enqueue(
                    mongo,
                    notification(type)
            );
        }

        verify(mongo, org.mockito.Mockito.times(7)).insert(
                ArgumentMatchers
                        .<MongoNotificationDeliveryRepository.DeliveryDocument>
                        anyCollection(),
                eq(MongoNotificationDeliveryRepository.COLLECTION)
        );
    }

    @Test
    void claimsJobsAndHandlesEmptyQueue() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoNotificationDeliveryRepository.DeliveryDocument.class),
                eq(MongoNotificationDeliveryRepository.COLLECTION)
        )).thenReturn(document(null))
                .thenReturn(null);
        var repository = new MongoNotificationDeliveryRepository(mongo);

        var claimed = repository.claim(
                "worker",
                NOW,
                NOW.plusSeconds(30),
                5
        );

        assertThat(claimed).isPresent();
        assertThat(claimed.orElseThrow().data()).isEmpty();
        assertThat(repository.claim(
                "worker",
                NOW,
                NOW.plusSeconds(30),
                5
        )).isEmpty();
    }

    @Test
    void resolvesEmailOnlyWithCurrentConsentAndVerifiedAddress() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findById(
                eq("user"),
                eq(MongoNotificationPreferenceRepository
                        .PreferenceDocument.class),
                eq(MongoNotificationPreferenceRepository.COLLECTION)
        )).thenReturn(
                null,
                preference(true, false, null, Set.of("STORY_UPDATES")),
                preference(true, false, NOW, null),
                preference(true, false, NOW, Set.of("COMMUNITY")),
                preference(false, false, NOW, Set.of("STORY_UPDATES")),
                preference(true, false, NOW, Set.of("STORY_UPDATES")),
                preference(true, false, NOW, Set.of("STORY_UPDATES")),
                preference(true, false, NOW, Set.of("STORY_UPDATES")),
                preference(true, false, NOW, Set.of("STORY_UPDATES"))
        );
        when(mongo.findById(
                eq("user"),
                eq(MongoNotificationDeliveryRepository
                        .EmailProjection.class),
                eq("users")
        )).thenReturn(
                null,
                new MongoNotificationDeliveryRepository.EmailProjection(
                        "user", "reader@example.com", false
                ),
                new MongoNotificationDeliveryRepository.EmailProjection(
                        "user", null, true
                ),
                new MongoNotificationDeliveryRepository.EmailProjection(
                        "user", "reader@example.com", true
                )
        );
        var repository = new MongoNotificationDeliveryRepository(mongo);
        var job = job(NotificationDeliveryRepository.Channel.EMAIL);

        for (int index = 0; index < 8; index++) {
            assertThat(repository.target(job)).isEmpty();
        }
        assertThat(repository.target(job))
                .contains(new NotificationDeliveryRepository.DeliveryTarget(
                        "reader@example.com"
                ));
    }

    @Test
    void resolvesPushOnlyWithConsentAndActiveSubscription() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findById(
                eq("user"),
                eq(MongoNotificationPreferenceRepository
                        .PreferenceDocument.class),
                eq(MongoNotificationPreferenceRepository.COLLECTION)
        )).thenReturn(
                preference(false, false, NOW, Set.of("STORY_UPDATES")),
                preference(false, true, NOW, Set.of("STORY_UPDATES")),
                preference(false, true, NOW, Set.of("STORY_UPDATES")),
                preference(false, true, NOW, Set.of("STORY_UPDATES"))
        );
        when(mongo.findOne(
                any(Query.class),
                eq(MongoNotificationDeliveryRepository.PushProjection.class),
                eq("notification_push_subscriptions")
        )).thenReturn(
                null,
                new MongoNotificationDeliveryRepository.PushProjection(
                        "legacy",
                        "https://push.example/legacy",
                        null,
                        null,
                        NOW
                ),
                new MongoNotificationDeliveryRepository.PushProjection(
                        "push",
                        "https://push.example/subscription",
                        "p256dh",
                        "auth",
                        NOW
                )
        );
        var repository = new MongoNotificationDeliveryRepository(mongo);
        var job = job(NotificationDeliveryRepository.Channel.PUSH);

        assertThat(repository.target(job)).isEmpty();
        assertThat(repository.target(job)).isEmpty();
        assertThat(repository.target(job)).isEmpty();
        assertThat(repository.target(job))
                .contains(new NotificationDeliveryRepository.DeliveryTarget(
                        "https://push.example/subscription",
                        Map.of("p256dh", "p256dh", "auth", "auth")
                ));
    }

    @Test
    void completesSuppressesRetriesAndDeadLettersWithLeaseGuard() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        UpdateResult changed = mock(UpdateResult.class);
        UpdateResult unchanged = mock(UpdateResult.class);
        when(changed.getModifiedCount()).thenReturn(1L);
        when(unchanged.getModifiedCount()).thenReturn(0L);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoNotificationDeliveryRepository.COLLECTION)
        )).thenReturn(changed, unchanged, changed, changed, changed);
        var repository = new MongoNotificationDeliveryRepository(mongo);
        var job = job(NotificationDeliveryRepository.Channel.EMAIL);

        assertThat(repository.complete(job, "worker", "provider-1", NOW))
                .isTrue();
        assertThat(repository.suppress(job, "worker", "NO_CONSENT", NOW))
                .isFalse();
        assertThat(repository.reschedule(
                job,
                "worker",
                "TEMPORARY",
                NOW.plusSeconds(30),
                false,
                NOW
        )).isTrue();
        assertThat(repository.reschedule(
                job,
                "worker",
                "PERMANENT",
                NOW,
                true,
                NOW
        )).isTrue();
    }

    private static MongoNotificationRepository.NotificationDocument
    notification(String type) {
        return new MongoNotificationRepository.NotificationDocument(
                "user:event:" + type,
                type,
                "user",
                "event:" + type,
                type,
                "Title",
                "Body",
                Map.of(),
                null,
                NOW
        );
    }

    private static MongoNotificationDeliveryRepository.DeliveryDocument
    document(Map<String, String> data) {
        return new MongoNotificationDeliveryRepository.DeliveryDocument(
                "delivery",
                "notification",
                "user",
                "EMAIL",
                "STORY_UPDATES",
                "STORY_PUBLISHED",
                "Title",
                "Body",
                data,
                "PROCESSING",
                1,
                NOW,
                NOW,
                "worker",
                NOW.plusSeconds(30),
                null,
                null,
                null
        );
    }

    private static NotificationDeliveryRepository.DeliveryJob job(
            NotificationDeliveryRepository.Channel channel
    ) {
        return new NotificationDeliveryRepository.DeliveryJob(
                "delivery",
                "notification",
                "user",
                channel,
                "STORY_UPDATES",
                "STORY_PUBLISHED",
                "Title",
                "Body",
                Map.of(),
                1
        );
    }

    private static MongoNotificationPreferenceRepository.PreferenceDocument
    preference(
            boolean email,
            boolean push,
            Instant consentedAt,
            Set<String> categories
    ) {
        return new MongoNotificationPreferenceRepository.PreferenceDocument(
                "user",
                email,
                push,
                categories,
                "notifications-2026.1",
                consentedAt,
                NOW,
                1
        );
    }
}
