package com.storyplatform.unit.publishing.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.publishing.infrastructure
        .PublishingPropagationHandler;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoPublishingPropagationRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoPublishingPropagationRepositoryTest {

    private static final String WORKER =
            "10000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void claimsDueEdgeTaskAndCompletesItsExactAttempt() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(Document.class),
                eq(PublishingPropagationHandler.COLLECTION)
        )).thenReturn(new Document()
                .append("_id", "event-1:EDGE")
                .append("eventId", "event-1")
                .append("targets", List.of("next:isr:story:one"))
                .append("attempts", 2));
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(PublishingPropagationHandler.COLLECTION)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var repository = new MongoPublishingPropagationRepository(mongo);

        var task = repository.claimEdge(
                WORKER, NOW, NOW.plusSeconds(30)
        ).orElseThrow();
        assertThat(task.attempts()).isEqualTo(2);
        assertThat(task.targets()).containsExactly(
                "next:isr:story:one"
        );
        assertThat(repository.complete(task, WORKER, NOW)).isTrue();
        assertThat(repository.fail(
                task,
                WORKER,
                NOW,
                NOW.plusSeconds(5),
                "TimeoutException",
                8
        )).isTrue();
    }

    @Test
    void emptyQueueAndStaleCompletionAreSafeNoops() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(PublishingPropagationHandler.COLLECTION)
        )).thenReturn(UpdateResult.acknowledged(0, 0L, null));
        var repository = new MongoPublishingPropagationRepository(mongo);

        assertThat(repository.claimEdge(
                WORKER, NOW, NOW.plusSeconds(30)
        )).isEmpty();
        assertThat(repository.complete(
                new com.storyplatform.publishing.application.port
                        .PublishingPropagationRepository.EdgeTask(
                        "task",
                        "event",
                        List.of(),
                        1
                ),
                WORKER,
                NOW
        )).isFalse();
        assertThat(repository.fail(
                new com.storyplatform.publishing.application.port
                        .PublishingPropagationRepository.EdgeTask(
                        "task", "event", List.of(), 8
                ),
                WORKER,
                NOW,
                NOW.plusSeconds(5),
                "TimeoutException",
                8
        )).isFalse();
    }
}
