package com.storyplatform.unit.community.infrastructure;

import com.storyplatform.community.application.StoryRelationService;
import com.storyplatform.community.domain.StoryRelation;
import com.storyplatform.community.infrastructure
        .StoryRelationCounterProjector;
import com.storyplatform.community.infrastructure.StoryRelationCounterStore;
import com.storyplatform.shared.events.OutboxDelivery;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StoryRelationInfrastructureTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T00:00:00Z");

    @Test
    void countersApplyBoundedDeltasAndAuthoritativeReconcile() {
        var mongo = mock(MongoTemplate.class);
        var store = new StoryRelationCounterStore(
                mongo,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        store.applyDelta("story", StoryRelation.Type.FAVORITE, 1);
        verify(mongo).upsert(
                any(Query.class),
                any(Update.class),
                eq(StoryRelationCounterStore.CounterDocument.class),
                eq(StoryRelationCounterStore.COLLECTION)
        );
        store.applyDelta("story", StoryRelation.Type.FAVORITE, -1);
        verify(mongo).updateFirst(
                any(Query.class),
                any(Update.class),
                eq(StoryRelationCounterStore.CounterDocument.class),
                eq(StoryRelationCounterStore.COLLECTION)
        );
        store.reconcile("story", 7, 3);
        assertThatThrownBy(() -> store.applyDelta(
                "story", StoryRelation.Type.FOLLOW, 0
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.reconcile("story", -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void projectorValidatesPayloadAndAggregateBinding() throws Exception {
        var counters = mock(StoryRelationCounterStore.class);
        var mapper = new ObjectMapper();
        var projector = new StoryRelationCounterProjector(
                counters,
                mapper
        );
        String payload = mapper.writeValueAsString(
                new StoryRelationService.RelationChanged(
                        "story",
                        "FOLLOW",
                        1
                )
        );

        assertThat(projector.consumer())
                .isEqualTo("story-relation-counter");
        assertThat(projector.eventVersion()).isEqualTo(1);
        projector.handle(delivery("story:user:FOLLOW", payload));
        verify(counters).applyDelta(
                "story",
                StoryRelation.Type.FOLLOW,
                1
        );
        assertThatThrownBy(() -> projector.handle(
                delivery("other:user:FOLLOW", payload)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> projector.handle(
                delivery("story:user:FOLLOW", "{broken")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static OutboxDelivery delivery(
            String aggregateId,
            String payload
    ) {
        return new OutboxDelivery(
                "event",
                StoryRelationService.EVENT_TYPE,
                1,
                NOW,
                "correlation",
                "story_relation",
                aggregateId,
                "user",
                null,
                "application/json",
                payload
        );
    }
}
