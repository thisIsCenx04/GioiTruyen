package com.storyplatform.unit.moderation.infrastructure;

import com.storyplatform.moderation.application.ModerationQueueOperations;
import com.storyplatform.moderation.application.port
        .ModerationQueueCursorCodec;
import com.storyplatform.moderation.infrastructure.persistence
        .MongoModerationQueueRepository;
import com.storyplatform.moderation.infrastructure.persistence
        .MongoModerationReviewDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoModerationQueueRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void listsClaimableCasesWithKeysetAndBoundedLimit() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.find(
                any(Query.class),
                eq(MongoModerationReviewDocument.class)
        )).thenReturn(List.of(document()));
        var repository = new MongoModerationQueueRepository(mongo);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);

        var results = repository.findClaimable(
                new ModerationQueueCursorCodec.Cursor(
                        90,
                        NOW,
                        "80000000-0000-4000-8000-000000000000"
                ),
                21,
                NOW
        );

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().priority()).isEqualTo(90);
        verify(mongo).find(
                query.capture(),
                eq(MongoModerationReviewDocument.class)
        );
        assertThat(query.getValue().getLimit()).isEqualTo(21);
        assertThat(query.getValue().getSortObject().toJson())
                .contains("\"priority\": -1");
    }

    @Test
    void atomicClaimAllowsOnlyOneReviewerForSameVersion() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoModerationReviewDocument.class)
        )).thenReturn(
                document(),
                (MongoModerationReviewDocument) null
        );
        var repository = new MongoModerationQueueRepository(mongo);

        var first = repository.claim(
                document().id(),
                "10000000-0000-4000-8000-000000000001",
                2,
                NOW,
                NOW.plusSeconds(900)
        );
        var second = repository.claim(
                document().id(),
                "10000000-0000-4000-8000-000000000002",
                2,
                NOW,
                NOW.plusSeconds(900)
        );

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
    }

    private static MongoModerationReviewDocument document() {
        return new MongoModerationReviewDocument(
                "80000000-0000-4000-8000-000000000001",
                "STORY",
                "40000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                "50000000-0000-4000-8000-000000000001",
                "OPEN",
                List.of(new ModerationQueueOperations.CheckSummary(
                        "SCHEMA",
                        "PASS",
                        "FROZEN_EVIDENCE_VALID",
                        "publishing-precheck-v1"
                )),
                false,
                90,
                null,
                null,
                NOW,
                NOW,
                2
        );
    }
}
