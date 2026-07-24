package com.storyplatform.unit.publishing.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.publishing.domain.PublishingReview;
import com.storyplatform.publishing.domain.PublishingSchedule;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoPublishingReviewDocument;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoPublishingScheduleDocument;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoPublishingScheduleRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
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

class MongoPublishingScheduleRepositoryTest {

    private static final String ACTOR =
            "10000000-0000-4000-8000-000000000001";
    private static final String TEAM =
            "20000000-0000-4000-8000-000000000001";
    private static final String STORY =
            "30000000-0000-4000-8000-000000000001";
    private static final String REVISION =
            "40000000-0000-4000-8000-000000000001";
    private static final String CHAPTER =
            "50000000-0000-4000-8000-000000000001";
    private static final String CHAPTER_REVISION =
            "60000000-0000-4000-8000-000000000001";
    private static final String SCHEDULE =
            "70000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void loadsActiveAndApprovedPinnedCandidate() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findOne(
                any(Query.class),
                eq(MongoPublishingScheduleDocument.class)
        )).thenReturn(MongoPublishingScheduleDocument.from(schedule()));
        when(mongo.findOne(
                any(Query.class),
                eq(Document.class),
                eq("stories")
        )).thenReturn(new Document("version", 3L));
        when(mongo.findOne(
                any(Query.class),
                eq(MongoPublishingReviewDocument.class)
        )).thenReturn(review(List.of(reviewChapter())));
        var repository = new MongoPublishingScheduleRepository(mongo);

        assertThat(repository.findActive(TEAM, STORY))
                .contains(schedule());
        var candidate = repository.findApproved(
                TEAM, STORY, REVISION
        ).orElseThrow();
        assertThat(candidate.storyVersion()).isEqualTo(3);
        assertThat(candidate.chapters()).containsExactly(chapter());
    }

    @Test
    void rejectsIncompleteApprovedCandidate() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var repository = new MongoPublishingScheduleRepository(mongo);
        assertThat(repository.findApproved(TEAM, STORY, REVISION))
                .isEmpty();

        when(mongo.findOne(
                any(Query.class),
                eq(Document.class),
                eq("stories")
        )).thenReturn(new Document("version", 3L));
        assertThat(repository.findApproved(TEAM, STORY, REVISION))
                .isEmpty();
        when(mongo.findOne(
                any(Query.class),
                eq(MongoPublishingReviewDocument.class)
        )).thenReturn(review(List.of()));
        assertThat(repository.findApproved(TEAM, STORY, REVISION))
                .isEmpty();
        when(mongo.findOne(
                any(Query.class),
                eq(MongoPublishingReviewDocument.class)
        )).thenReturn(review(List.of(reviewChapter())));
        when(mongo.findOne(
                any(Query.class),
                eq(Document.class),
                eq("stories")
        )).thenReturn(new Document());
        assertThat(repository.findApproved(TEAM, STORY, REVISION))
                .isEmpty();
    }

    @Test
    void createsOnlyWhenStoryAndEveryChapterArePinned() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var repository = new MongoPublishingScheduleRepository(mongo);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq("stories")
        )).thenReturn(updated(1, 1));
        when(mongo.updateMulti(
                any(Query.class),
                any(Update.class),
                eq("chapters")
        )).thenReturn(updated(1, 1));

        assertThat(repository.create(candidate(), schedule())).isTrue();
        verify(mongo).insert(any(MongoPublishingScheduleDocument.class));

        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq("stories")
        )).thenReturn(updated(0, 0));
        assertThat(repository.create(candidate(), schedule())).isFalse();

        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq("stories")
        )).thenReturn(updated(1, 1));
        when(mongo.updateMulti(
                any(Query.class),
                any(Update.class),
                eq("chapters")
        )).thenReturn(updated(0, 0));
        assertThat(repository.create(candidate(), schedule())).isFalse();
    }

    @Test
    void rescheduleAndCancelRequireEveryPinnedTarget() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var repository = new MongoPublishingScheduleRepository(mongo);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoPublishingScheduleDocument.class)
        )).thenReturn(updated(1, 1));
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq("stories")
        )).thenReturn(updated(1, 1));
        when(mongo.updateMulti(
                any(Query.class),
                any(Update.class),
                eq("chapters")
        )).thenReturn(updated(1, 1));

        assertThat(repository.reschedule(
                schedule(),
                NOW.plusSeconds(7200),
                "UTC",
                NOW.plusSeconds(1),
                1
        )).isTrue();
        assertThat(repository.cancel(
                schedule(), NOW.plusSeconds(2), 1
        )).isTrue();

        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoPublishingScheduleDocument.class)
        )).thenReturn(updated(0, 0));
        assertThat(repository.cancel(
                schedule(), NOW.plusSeconds(2), 1
        )).isFalse();
        verify(mongo, never()).remove(any(Query.class), eq("stories"));
    }

    private static UpdateResult updated(long matched, long modified) {
        return UpdateResult.acknowledged(matched, modified, null);
    }

    private static com.storyplatform.publishing.application.port
            .PublishingScheduleRepository.ApprovedCandidate candidate() {
        return new com.storyplatform.publishing.application.port
                .PublishingScheduleRepository.ApprovedCandidate(
                STORY, TEAM, REVISION, 3, List.of(chapter())
        );
    }

    private static PublishingSchedule schedule() {
        return new PublishingSchedule(
                SCHEDULE,
                PublishingSchedule.TargetType.STORY,
                STORY,
                TEAM,
                REVISION,
                List.of(chapter()),
                PublishingSchedule.State.SCHEDULED,
                NOW.plusSeconds(3600),
                "UTC",
                ACTOR,
                NOW,
                NOW,
                1
        );
    }

    private static PublishingSchedule.FrozenChapterRevision chapter() {
        return new PublishingSchedule.FrozenChapterRevision(
                CHAPTER, CHAPTER_REVISION, 1
        );
    }

    private static PublishingReview.ChapterRevisionRef reviewChapter() {
        return new PublishingReview.ChapterRevisionRef(
                CHAPTER, CHAPTER_REVISION, 1
        );
    }

    private static MongoPublishingReviewDocument review(
            List<PublishingReview.ChapterRevisionRef> chapters
    ) {
        return new MongoPublishingReviewDocument(
                "80000000-0000-4000-8000-000000000001",
                PublishingReview.TargetType.STORY,
                STORY,
                TEAM,
                REVISION,
                2,
                chapters,
                PublishingReview.State.APPROVED,
                List.of(),
                false,
                null,
                null,
                null,
                null,
                null,
                ACTOR,
                NOW,
                NOW,
                NOW,
                4,
                "submit-request-001",
                "a".repeat(64)
        );
    }
}
