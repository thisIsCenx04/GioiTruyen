package com.storyplatform.unit.publishing.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.publishing.application.PublishingPrecheckEngine;
import com.storyplatform.publishing.application.port
        .PublishingPrecheckRepository;
import com.storyplatform.publishing.domain.PublishingReview;
import com.storyplatform.publishing.domain.StoryDraft;
import com.storyplatform.publishing.domain.StoryRevision;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoChapterRevisionDocument;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoPublishingPrecheckRepository;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoPublishingReviewDocument;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoStoryRevisionDocument;
import org.bson.Document;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoPublishingPrecheckRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void atomicallyClaimsPendingOrExpiredReview() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoPublishingReviewDocument.class)
        )).thenReturn(document());
        var repository = new MongoPublishingPrecheckRepository(mongo);

        var claimed = repository.claim(
                "worker-1",
                NOW,
                NOW.plusSeconds(30)
        ).orElseThrow();

        assertThat(claimed.reviewId()).isEqualTo(document().id());
        assertThat(claimed.storyRevision())
                .isEqualTo(document().submittedRevision());
        assertThat(claimed.chapters()).hasSize(1);
    }

    @Test
    void resolvesFrozenStoryChapterAndApprovedCoverEvidence() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var review = claimed();
        when(mongo.findOne(
                any(Query.class),
                eq(MongoStoryRevisionDocument.class)
        )).thenReturn(storyRevision());
        when(mongo.find(
                any(Query.class),
                eq(MongoChapterRevisionDocument.class)
        )).thenReturn(List.of(chapterRevision()));
        when(mongo.findOne(
                any(Query.class),
                eq(Document.class),
                eq("media_assets")
        )).thenReturn(new Document(Map.of(
                "ownerType", "TEAM",
                "ownerId", review.teamId(),
                "purpose", "STORY_COVER",
                "state", "READY",
                "moderationState", "APPROVED"
        )));
        var repository = new MongoPublishingPrecheckRepository(mongo);

        var evidence = repository.loadEvidence(review).orElseThrow();

        assertThat(evidence.storyTitle())
                .isEqualTo(storyRevision().snapshot().title());
        assertThat(evidence.storySynopsis())
                .isEqualTo(storyRevision().snapshot().synopsis());
        assertThat(evidence.coverAssetId())
                .isEqualTo(storyRevision().snapshot().coverAssetId());
        assertThat(evidence.cover().moderationState())
                .isEqualTo("APPROVED");
        assertThat(evidence.chapters()).hasSize(1);
        assertThat(evidence.chapters().getFirst().checksum())
                .isEqualTo("a".repeat(64));
    }

    @Test
    void missingFrozenStoryRevisionReturnsNoEvidence() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var repository = new MongoPublishingPrecheckRepository(mongo);

        assertThat(repository.loadEvidence(claimed())).isEmpty();
    }

    @Test
    void completesOnlyTheOwnedRunningLease() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoPublishingReviewDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var repository = new MongoPublishingPrecheckRepository(mongo);
        var checks = List.of(new PublishingPrecheckEngine.CheckResult(
                PublishingPrecheckEngine.Rule.SCHEMA,
                PublishingPrecheckEngine.Outcome.PASS,
                "FROZEN_EVIDENCE_VALID",
                "publishing-precheck-v1"
        ));

        assertThat(repository.complete(
                claimed(),
                "worker-1",
                checks,
                false,
                NOW
        )).isTrue();

        verify(mongo).updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoPublishingReviewDocument.class)
        );
    }

    private static PublishingPrecheckRepository.ClaimedReview claimed() {
        return new PublishingPrecheckRepository.ClaimedReview(
                document().id(),
                document().teamId(),
                document().targetId(),
                document().submittedRevision(),
                List.of(new PublishingPrecheckRepository.FrozenChapter(
                        document().chapterRevisions()
                                .getFirst().chapterId(),
                        document().chapterRevisions()
                                .getFirst().revisionId(),
                        1
                )),
                2,
                NOW.plusSeconds(30)
        );
    }

    private static MongoPublishingReviewDocument document() {
        PublishingReview review = new PublishingReview(
                "80000000-0000-4000-8000-000000000001",
                PublishingReview.TargetType.STORY,
                "40000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                "50000000-0000-4000-8000-000000000001",
                2,
                List.of(new PublishingReview.ChapterRevisionRef(
                        "60000000-0000-4000-8000-000000000001",
                        "70000000-0000-4000-8000-000000000001",
                        1
                )),
                PublishingReview.State.AUTOMATED_CHECK_PENDING,
                "10000000-0000-4000-8000-000000000001",
                NOW,
                1,
                "submit-request-001",
                "a".repeat(64)
        );
        return MongoPublishingReviewDocument.from(review);
    }

    private static MongoStoryRevisionDocument storyRevision() {
        return new MongoStoryRevisionDocument(
                document().submittedRevision(),
                document().targetId(),
                2,
                new StoryRevision.Snapshot(
                        "Story",
                        "Synopsis",
                        StoryDraft.Origin.ORIGINAL,
                        "vi",
                        List.of(
                                "30000000-0000-4000-8000-000000000002"
                        ),
                        "30000000-0000-4000-8000-000000000001"
                ),
                document().submittedBy(),
                "b".repeat(64),
                NOW
        );
    }

    private static MongoChapterRevisionDocument chapterRevision() {
        return new MongoChapterRevisionDocument(
                document().chapterRevisions().getFirst().revisionId(),
                document().chapterRevisions().getFirst().chapterId(),
                1,
                "<p>Hello world</p>",
                "Hello world",
                "a".repeat(64),
                document().submittedBy(),
                NOW
        );
    }
}
