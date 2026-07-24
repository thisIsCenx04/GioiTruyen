package com.storyplatform.unit.publishing.infrastructure;

import com.storyplatform.publishing.infrastructure
        .PublishingPropagationHandler;
import com.storyplatform.shared.events.OutboxDelivery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PublishingPropagationHandlerTest {

    @Test
    void usesStableEventChannelKeyForIdempotentEdgeRequests() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var handler = new PublishingPropagationHandler(
                mongo,
                "publishing.chapter.published",
                PublishingPropagationHandler.Channel.EDGE
        );
        ArgumentCaptor<Query> queries =
                ArgumentCaptor.forClass(Query.class);

        handler.handle(event("publishing.chapter.published", "chapter"));
        handler.handle(event("publishing.chapter.published", "chapter"));

        verify(mongo, times(2)).upsert(
                queries.capture(),
                any(Update.class),
                eq(PublishingPropagationHandler.COLLECTION)
        );
        assertThat(queries.getAllValues())
                .extracting(value -> value.getQueryObject().getString("_id"))
                .containsOnly("event-1:EDGE");
        assertThat(handler.consumer()).isEqualTo("publishing-edge-v1");
        assertThat(handler.eventVersion()).isOne();
    }

    @Test
    void buildsNotificationIntentAndRejectsWrongEvent() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var handler = new PublishingPropagationHandler(
                mongo,
                "publishing.visibility.changed",
                PublishingPropagationHandler.Channel.NOTIFICATION
        );
        handler.handle(event("publishing.visibility.changed", "story"));
        verify(mongo).upsert(
                any(Query.class),
                any(Update.class),
                eq(PublishingPropagationHandler.COLLECTION)
        );
        assertThat(handler.consumer())
                .isEqualTo("publishing-notification-v1");
        assertThatThrownBy(() -> handler.handle(
                event("publishing.chapter.published", "chapter")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static OutboxDelivery event(
            String eventType,
            String aggregateType
    ) {
        return new OutboxDelivery(
                "event-1",
                eventType,
                1,
                Instant.parse("2026-07-24T00:00:00Z"),
                "correlation",
                aggregateType,
                "aggregate-1",
                null,
                "team-1",
                "application/json",
                "{}"
        );
    }
}
