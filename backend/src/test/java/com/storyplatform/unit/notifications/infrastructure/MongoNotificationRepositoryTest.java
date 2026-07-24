package com.storyplatform.unit.notifications.infrastructure;

import com.storyplatform.notifications.application.port
        .NotificationCursorCodec;
import com.storyplatform.notifications.infrastructure.persistence
        .MongoNotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoNotificationRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void keysetPageAppliesWatermarkAndHasMore() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findById(
                "user",
                MongoNotificationRepository.NotificationState.class,
                MongoNotificationRepository.STATE_COLLECTION
        )).thenReturn(new MongoNotificationRepository.NotificationState(
                "user", NOW.minusSeconds(1)
        ));
        when(mongo.find(
                any(Query.class),
                eq(MongoNotificationRepository.NotificationDocument.class),
                eq(MongoNotificationRepository.COLLECTION)
        )).thenReturn(List.of(
                document("three", NOW),
                document("two", NOW.minusSeconds(1)),
                document("one", NOW.minusSeconds(2))
        ));
        var repository = new MongoNotificationRepository(mongo);

        var page = repository.list(
                "user",
                2,
                new NotificationCursorCodec.Position(
                        "user", NOW.plusSeconds(1), "after"
                )
        );

        assertThat(page.hasMore()).isTrue();
        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).readAt()).isNull();
        assertThat(page.items().get(1).readAt())
                .isEqualTo(NOW.minusSeconds(1));
    }

    @Test
    void marksOneAndWatermarkIdempotently() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoNotificationRepository.NotificationDocument.class),
                eq(MongoNotificationRepository.COLLECTION)
        )).thenReturn(document("id", NOW), null);
        when(mongo.findOne(
                any(Query.class),
                eq(MongoNotificationRepository.NotificationDocument.class),
                eq(MongoNotificationRepository.COLLECTION)
        )).thenReturn(document("id", NOW));
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoNotificationRepository.NotificationState.class),
                eq(MongoNotificationRepository.STATE_COLLECTION)
        )).thenReturn(new MongoNotificationRepository.NotificationState(
                "user", NOW
        ));
        var repository = new MongoNotificationRepository(mongo);

        assertThat(repository.markRead("user", "id", NOW)).isPresent();
        assertThat(repository.markRead("user", "id", NOW)).isPresent();
        assertThat(repository.markAllRead("user", NOW)).isEqualTo(NOW);
    }

    @Test
    void countsUnreadAfterWatermarkAndDeduplicatesReplay() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findById(
                "user",
                MongoNotificationRepository.NotificationState.class,
                MongoNotificationRepository.STATE_COLLECTION
        )).thenReturn(null);
        when(mongo.count(
                any(Query.class),
                eq(MongoNotificationRepository.COLLECTION)
        )).thenReturn(3L);
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoNotificationRepository.NotificationDocument.class),
                eq(MongoNotificationRepository.COLLECTION)
        )).thenReturn(null, document("existing", NOW));
        var repository = new MongoNotificationRepository(mongo);

        assertThat(repository.unreadCount("user")).isEqualTo(3);
        var created = repository.saveIfAbsent(
                "new",
                "user",
                "event:1",
                "STORY_PUBLISHED",
                "Title",
                "Body",
                Map.of(),
                NOW
        );
        assertThat(created.created()).isTrue();
        assertThat(created.notification().id()).isEqualTo("new");
        var replay = repository.saveIfAbsent(
                "other",
                "user",
                "event:1",
                "STORY_PUBLISHED",
                "Changed",
                "Changed",
                Map.of(),
                NOW
        );
        assertThat(replay.created()).isFalse();
        assertThat(replay.notification().id()).isEqualTo("existing");
    }

    private static MongoNotificationRepository.NotificationDocument document(
            String id,
            Instant createdAt
    ) {
        return new MongoNotificationRepository.NotificationDocument(
                "user:event:" + id,
                id,
                "user",
                "event:" + id,
                "STORY_PUBLISHED",
                "Title",
                "Body",
                Map.of(),
                null,
                createdAt
        );
    }
}
