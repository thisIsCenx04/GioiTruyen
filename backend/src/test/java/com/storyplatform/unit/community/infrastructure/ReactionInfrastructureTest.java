package com.storyplatform.unit.community.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.community.application.ReactionService;
import com.storyplatform.community.domain.Reaction;
import com.storyplatform.community.infrastructure.ReactionCounterProjector;
import com.storyplatform.community.infrastructure.ReactionCounterStore;
import com.storyplatform.community.infrastructure.persistence
        .MongoReactionRepository;
import com.storyplatform.shared.events.OutboxDelivery;
import org.bson.BsonString;
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
import static org.mockito.Mockito.when;

class ReactionInfrastructureTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T06:00:00Z");

    @Test
    void repositoryUsesUniqueUpsertAndTargetScopedQueries() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.exists(any(Query.class), eq("comments")))
                .thenReturn(true);
        when(mongo.exists(any(Query.class), eq("stories")))
                .thenReturn(true);
        when(mongo.exists(any(Query.class), eq("chapters")))
                .thenReturn(true);
        when(mongo.upsert(
                any(Query.class),
                any(Update.class),
                eq(MongoReactionRepository.ReactionDocument.class),
                eq(MongoReactionRepository.COLLECTION)
        )).thenReturn(UpdateResult.acknowledged(
                0,
                0L,
                new BsonString("created")
        ));
        MongoReactionRepository repository =
                new MongoReactionRepository(mongo);

        assertThat(repository.targetIsVisible(
                Reaction.TargetType.COMMENT, "target"
        )).isTrue();
        assertThat(repository.targetIsVisible(
                Reaction.TargetType.STORY, "target"
        )).isTrue();
        assertThat(repository.targetIsVisible(
                Reaction.TargetType.CHAPTER, "target"
        )).isTrue();
        assertThat(repository.insertIfAbsent(Reaction.create(
                Reaction.TargetType.COMMENT,
                "target",
                "actor",
                NOW
        ))).isTrue();
    }

    @Test
    void counterAppliesBoundedDeltasAndProjectorValidatesBinding()
            throws Exception {
        MongoTemplate mongo = mock(MongoTemplate.class);
        ReactionCounterStore counters = new ReactionCounterStore(
                mongo,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        counters.applyDelta(Reaction.TargetType.COMMENT, "target", 1);
        verify(mongo).upsert(
                any(Query.class),
                any(Update.class),
                eq(ReactionCounterStore.CounterDocument.class),
                eq(ReactionCounterStore.COLLECTION)
        );
        counters.applyDelta(Reaction.TargetType.COMMENT, "target", -1);
        verify(mongo).updateFirst(
                any(Query.class),
                any(Update.class),
                eq(ReactionCounterStore.CounterDocument.class),
                eq(ReactionCounterStore.COLLECTION)
        );
        assertThatThrownBy(() -> counters.applyDelta(
                Reaction.TargetType.COMMENT, "target", 0
        )).isInstanceOf(IllegalArgumentException.class);

        ObjectMapper mapper = new ObjectMapper();
        ReactionCounterProjector projector =
                new ReactionCounterProjector(counters, mapper);
        String payload = mapper.writeValueAsString(
                new ReactionService.ReactionChanged(
                        "COMMENT",
                        "target",
                        1
                )
        );
        assertThat(projector.consumer()).isEqualTo("reaction-counter");
        assertThat(projector.eventVersion()).isEqualTo(1);
        projector.handle(delivery(
                "COMMENT:target:actor",
                payload
        ));
        assertThatThrownBy(() -> projector.handle(delivery(
                "COMMENT:other:actor",
                payload
        ))).isInstanceOf(IllegalArgumentException.class);
    }

    private static OutboxDelivery delivery(
            String aggregateId,
            String payload
    ) {
        return new OutboxDelivery(
                "event",
                ReactionService.EVENT_TYPE,
                1,
                NOW,
                "correlation",
                "reaction",
                aggregateId,
                "actor",
                null,
                "application/json",
                payload
        );
    }
}
