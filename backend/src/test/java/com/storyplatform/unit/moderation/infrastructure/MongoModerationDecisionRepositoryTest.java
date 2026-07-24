package com.storyplatform.unit.moderation.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.moderation.application.ModerationDecisionOperations;
import com.storyplatform.moderation.application.port
        .ModerationDecisionRepository;
import com.storyplatform.moderation.infrastructure.persistence
        .MongoModerationAuditDocument;
import com.storyplatform.moderation.infrastructure.persistence
        .MongoModerationChapterDocument;
import com.storyplatform.moderation.infrastructure.persistence
        .MongoModerationDecisionRepository;
import com.storyplatform.moderation.infrastructure.persistence
        .MongoModerationReviewDocument;
import com.storyplatform.moderation.infrastructure.persistence
        .MongoModerationStoryDocument;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoModerationDecisionRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private static final String REVIEWER =
            "10000000-0000-4000-8000-000000000001";

    @Test
    void eachDecisionTransitionsFrozenTargetsAndWritesAudit() {
        var decisions = List.of(
                ModerationDecisionOperations.Decision.APPROVE,
                ModerationDecisionOperations.Decision.REQUEST_CHANGES,
                ModerationDecisionOperations.Decision.REJECT
        );
        var expectedStates = List.of(
                "APPROVED",
                "CHANGES_REQUESTED",
                "REJECTED"
        );
        for (int index = 0; index < decisions.size(); index++) {
            MongoTemplate mongo = successfulMongo();
            var repository = repository(mongo);

            var result = repository.decide(
                    review().id(),
                    REVIEWER,
                    3,
                    decision(decisions.get(index)),
                    NOW
            );

            assertThat(result.outcome()).isEqualTo(
                    ModerationDecisionRepository.Outcome.SUCCESS
            );
            assertThat(result.state()).isEqualTo(expectedStates.get(index));
            verify(mongo).insert(any(MongoModerationAuditDocument.class));
        }
    }

    @Test
    void atomicDecisionPredicateAllowsOnlyOneDecision() {
        MongoTemplate mongo = successfulMongo();
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoModerationReviewDocument.class)
        )).thenReturn(
                review(),
                (MongoModerationReviewDocument) null
        );
        var repository = repository(mongo);

        var first = repository.decide(
                review().id(), REVIEWER, 3, decision(
                        ModerationDecisionOperations.Decision.APPROVE
                ), NOW
        );
        var second = repository.decide(
                review().id(), REVIEWER, 3, decision(
                        ModerationDecisionOperations.Decision.REJECT
                ), NOW
        );

        assertThat(first.outcome()).isEqualTo(
                ModerationDecisionRepository.Outcome.SUCCESS
        );
        assertThat(second.outcome()).isEqualTo(
                ModerationDecisionRepository.Outcome.CONFLICT
        );
    }

    @Test
    void staleStoryRollsBackThroughServiceConflictPath() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoModerationReviewDocument.class)
        )).thenReturn(review());
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoModerationStoryDocument.class)
        )).thenReturn(UpdateResult.acknowledged(0, 0L, null));
        var repository = repository(mongo);

        var result = repository.decide(
                review().id(), REVIEWER, 3, decision(
                        ModerationDecisionOperations.Decision.APPROVE
                ), NOW
        );

        assertThat(result.outcome()).isEqualTo(
                ModerationDecisionRepository.Outcome.STALE_TARGET
        );
        verify(mongo, never()).insert(
                any(MongoModerationAuditDocument.class)
        );
    }

    private static MongoTemplate successfulMongo() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoModerationReviewDocument.class)
        )).thenReturn(review());
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoModerationStoryDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(mongo.updateMulti(
                any(Query.class),
                any(Update.class),
                eq(MongoModerationChapterDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        return mongo;
    }

    private static MongoModerationDecisionRepository repository(
            MongoTemplate mongo
    ) {
        return new MongoModerationDecisionRepository(
                mongo,
                () -> "90000000-0000-4000-8000-000000000001"
        );
    }

    private static ModerationDecisionRepository.DecisionRecord decision(
            ModerationDecisionOperations.Decision value
    ) {
        return new ModerationDecisionRepository.DecisionRecord(
                value,
                "POLICY_REVIEWED",
                value == ModerationDecisionOperations.Decision.APPROVE
                        ? null
                        : "Explanation",
                List.of("evidence:123"),
                "publishing-2026.1"
        );
    }

    private static MongoModerationReviewDocument review() {
        return new MongoModerationReviewDocument(
                "80000000-0000-4000-8000-000000000001",
                "STORY",
                "40000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                "50000000-0000-4000-8000-000000000001",
                2,
                List.of(new MongoModerationReviewDocument
                        .FrozenChapterRevision(
                        "60000000-0000-4000-8000-000000000001",
                        "70000000-0000-4000-8000-000000000001",
                        1
                )),
                "CLAIMED",
                List.of(),
                false,
                90,
                REVIEWER,
                NOW.plusSeconds(900),
                NOW.minusSeconds(60),
                NOW,
                4,
                null
        );
    }
}
