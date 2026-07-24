package com.storyplatform.unit.moderation.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.moderation.application.ModerationAppealOperations;
import com.storyplatform.moderation.application.port
        .ModerationAppealRepository;
import com.storyplatform.moderation.infrastructure.persistence
        .MongoModerationAppealDocument;
import com.storyplatform.moderation.infrastructure.persistence
        .MongoModerationAppealRepository;
import com.storyplatform.moderation.infrastructure.persistence
        .MongoModerationReviewDocument;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoModerationAppealRepositoryTest {

    private static final String REVIEW = "review";
    private static final String APPEAL = "appeal";
    private static final String ACTOR = "actor";
    private static final String ORIGINAL = "original";
    private static final String REVIEWER = "reviewer";
    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void verifiesEligibleActiveTeamMember() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var review = mock(MongoModerationReviewDocument.class);
        var decision = mock(
                MongoModerationReviewDocument
                        .ModerationDecisionDocument.class
        );
        when(review.id()).thenReturn(REVIEW);
        when(review.teamId()).thenReturn("team");
        when(review.decision()).thenReturn(decision);
        when(decision.reviewerId()).thenReturn(ORIGINAL);
        when(decision.decidedAt()).thenReturn(NOW);
        when(mongo.findOne(
                any(Query.class),
                eq(MongoModerationReviewDocument.class)
        )).thenReturn(review);
        when(mongo.exists(any(Query.class), eq("team_memberships")))
                .thenReturn(true);
        var repository = new MongoModerationAppealRepository(mongo);

        assertThat(repository.eligibleReview(REVIEW, ACTOR))
                .contains(new ModerationAppealRepository.EligibleReview(
                        REVIEW,
                        ORIGINAL,
                        NOW
                ));

        when(mongo.exists(any(Query.class), eq("team_memberships")))
                .thenReturn(false);
        assertThat(repository.eligibleReview(REVIEW, ACTOR)).isEmpty();
        when(mongo.findOne(
                any(Query.class),
                eq(MongoModerationReviewDocument.class)
        )).thenReturn(null);
        assertThat(repository.eligibleReview(REVIEW, ACTOR)).isEmpty();
    }

    @Test
    void createsOnceAndReturnsExistingOnUniqueRace() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.insert(any(MongoModerationAppealDocument.class)))
                .thenAnswer(call -> call.getArgument(0));
        var repository = new MongoModerationAppealRepository(mongo);
        var eligible = new ModerationAppealRepository.EligibleReview(
                REVIEW,
                ORIGINAL,
                NOW
        );

        var created = repository.createIfAbsent(
                APPEAL,
                eligible,
                ACTOR,
                "Reason",
                NOW,
                NOW.plusSeconds(60)
        );
        assertThat(created.outcome())
                .isEqualTo(ModerationAppealRepository.Outcome.SUCCESS);
        assertThat(created.appeal().status()).isEqualTo("PENDING");

        org.mockito.Mockito.reset(mongo);
        doThrow(new DuplicateKeyException("duplicate"))
                .when(mongo)
                .insert(any(MongoModerationAppealDocument.class));
        when(mongo.findOne(
                any(Query.class),
                eq(MongoModerationAppealDocument.class)
        )).thenReturn(document("PENDING", null));
        var duplicate = repository.createIfAbsent(
                "other",
                eligible,
                ACTOR,
                "Reason",
                NOW,
                NOW.plusSeconds(60)
        );
        assertThat(duplicate.outcome())
                .isEqualTo(ModerationAppealRepository.Outcome.DUPLICATE);
        assertThat(duplicate.appeal().id()).isEqualTo(APPEAL);
    }

    @Test
    void atomicallyFinalizesWithIndependentReviewer() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoModerationAppealDocument.class)
        )).thenReturn(
                document("UPHELD", "UPHOLD"),
                document("OVERTURNED", "OVERTURN"),
                null
        );
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoModerationReviewDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var repository = new MongoModerationAppealRepository(mongo);

        var upheld = repository.resolve(
                REVIEW,
                APPEAL,
                REVIEWER,
                ModerationAppealOperations.AppealDecision.UPHOLD,
                "POLICY_CONFIRMED",
                "Final",
                NOW
        );
        assertThat(upheld.appeal().decision())
                .isEqualTo(ModerationAppealOperations
                        .AppealDecision.UPHOLD);
        var overturned = repository.resolve(
                REVIEW,
                APPEAL,
                REVIEWER,
                ModerationAppealOperations.AppealDecision.OVERTURN,
                "NEW_EVIDENCE",
                "Accepted",
                NOW
        );
        assertThat(overturned.appeal().status()).isEqualTo("OVERTURNED");
        assertThat(repository.resolve(
                REVIEW,
                APPEAL,
                REVIEWER,
                ModerationAppealOperations.AppealDecision.UPHOLD,
                "POLICY_CONFIRMED",
                "Final",
                NOW
        ).outcome()).isEqualTo(
                ModerationAppealRepository.Outcome.CONFLICT
        );
        verify(mongo, org.mockito.Mockito.times(2)).updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoModerationReviewDocument.class)
        );
    }

    private static MongoModerationAppealDocument document(
            String status,
            String decision
    ) {
        return new MongoModerationAppealDocument(
                APPEAL,
                REVIEW,
                ACTOR,
                ORIGINAL,
                "Reason",
                status,
                decision,
                decision == null ? null : "POLICY_CONFIRMED",
                decision == null ? null : "Final",
                decision == null ? null : REVIEWER,
                NOW,
                NOW.plusSeconds(60),
                decision == null ? null : NOW
        );
    }
}
