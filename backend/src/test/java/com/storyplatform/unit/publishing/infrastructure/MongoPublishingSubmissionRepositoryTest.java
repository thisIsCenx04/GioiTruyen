package com.storyplatform.unit.publishing.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.publishing.application.port
        .PublishingSubmissionRepository;
import com.storyplatform.publishing.domain.ChapterDraft;
import com.storyplatform.publishing.domain.PublishingReview;
import com.storyplatform.publishing.domain.StoryDraft;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoChapterDraftDocument;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoPublishingReviewDocument;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoPublishingSubmissionRepository;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoStoryDraftDocument;
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

class MongoPublishingSubmissionRepositoryTest {

    private static final String TEAM =
            "20000000-0000-4000-8000-000000000001";
    private static final String STORY =
            "40000000-0000-4000-8000-000000000001";
    private static final String STORY_REVISION =
            "50000000-0000-4000-8000-000000000001";
    private static final String CHAPTER =
            "60000000-0000-4000-8000-000000000001";
    private static final String CHAPTER_REVISION =
            "70000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void loadsOrderedFrozenCandidate() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findOne(
                any(Query.class),
                eq(MongoStoryDraftDocument.class)
        )).thenReturn(story());
        when(mongo.find(
                any(Query.class),
                eq(MongoChapterDraftDocument.class)
        )).thenReturn(List.of(chapter()));
        var repository = new MongoPublishingSubmissionRepository(mongo);

        var candidate = repository.findCandidate(TEAM, STORY)
                .orElseThrow();

        assertThat(candidate.storyRevision()).isEqualTo(STORY_REVISION);
        assertThat(candidate.chapterRevisions()).containsExactly(
                new PublishingReview.ChapterRevisionRef(
                        CHAPTER,
                        CHAPTER_REVISION,
                        1
                )
        );
    }

    @Test
    void atomicallyTransitionsExactRevisionBeforeReviewInsert() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoStoryDraftDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var repository = new MongoPublishingSubmissionRepository(mongo);

        assertThat(repository.submit(candidate(), review())).isTrue();

        verify(mongo).insert(any(MongoPublishingReviewDocument.class));
    }

    @Test
    void staleRevisionDoesNotInsertReview() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoStoryDraftDocument.class)
        )).thenReturn(UpdateResult.acknowledged(0, 0L, null));
        var repository = new MongoPublishingSubmissionRepository(mongo);

        assertThat(repository.submit(candidate(), review())).isFalse();

        verify(mongo, never()).insert(
                any(MongoPublishingReviewDocument.class)
        );
    }

    private static MongoStoryDraftDocument story() {
        return new MongoStoryDraftDocument(
                STORY,
                TEAM,
                "story",
                "Story",
                List.of(),
                "Synopsis",
                List.of("30000000-0000-4000-8000-000000000001"),
                StoryDraft.Origin.ORIGINAL,
                "vi",
                StoryDraft.CompletionStatus.ONGOING,
                StoryDraft.WorkflowStatus.DRAFT,
                STORY_REVISION,
                2,
                null,
                null,
                NOW,
                NOW,
                2,
                "10000000-0000-4000-8000-000000000001",
                "draft-request-001",
                "a".repeat(64)
        );
    }

    private static MongoChapterDraftDocument chapter() {
        return new MongoChapterDraftDocument(
                CHAPTER,
                STORY,
                TEAM,
                1,
                "chapter-1-start-60000000",
                "Start",
                ChapterDraft.WorkflowStatus.DRAFT,
                CHAPTER_REVISION,
                1,
                null,
                null,
                NOW,
                NOW,
                1
        );
    }

    private static PublishingSubmissionRepository.SubmissionCandidate
            candidate() {
        return new PublishingSubmissionRepository.SubmissionCandidate(
                STORY,
                TEAM,
                STORY_REVISION,
                2,
                List.of(new PublishingReview.ChapterRevisionRef(
                        CHAPTER,
                        CHAPTER_REVISION,
                        1
                ))
        );
    }

    private static PublishingReview review() {
        return new PublishingReview(
                "80000000-0000-4000-8000-000000000001",
                PublishingReview.TargetType.STORY,
                STORY,
                TEAM,
                STORY_REVISION,
                2,
                candidate().chapterRevisions(),
                PublishingReview.State.AUTOMATED_CHECK_PENDING,
                "10000000-0000-4000-8000-000000000001",
                NOW,
                1,
                "submit-request-001",
                "a".repeat(64)
        );
    }
}
