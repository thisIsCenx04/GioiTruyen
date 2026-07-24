package com.storyplatform.unit.publishing.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.publishing.domain.StoryDraft;
import com.storyplatform.publishing.domain.StoryRevision;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoStoryDraftRepository;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoStoryDraftDocument;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoStoryRevisionDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoStoryDraftRepositoryTest {

    @Test
    void storesStoryAndRevisionAsSeparateDocuments() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoStoryDraftRepository repository =
                new MongoStoryDraftRepository(mongo);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        StoryDraft story = new StoryDraft(
                "40000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                "truyen-mot-40000000",
                "Truyện Một",
                "Tóm tắt",
                List.of("30000000-0000-4000-8000-000000000001"),
                StoryDraft.Origin.ORIGINAL,
                "vi",
                StoryDraft.CompletionStatus.ONGOING,
                StoryDraft.WorkflowStatus.DRAFT,
                "50000000-0000-4000-8000-000000000001",
                null,
                now,
                now,
                1
        );
        StoryRevision revision = new StoryRevision(
                story.currentRevision(),
                story.id(),
                1,
                new StoryRevision.Snapshot(
                        story.title(),
                        story.synopsis(),
                        story.origin(),
                        story.language(),
                        story.categoryIds(),
                        null
                ),
                "10000000-0000-4000-8000-000000000001",
                "a".repeat(64),
                now
        );

        repository.insert(
                story,
                revision,
                revision.createdBy(),
                "draft-request-001",
                "b".repeat(64)
        );

        verify(mongo).insert(any(MongoStoryDraftDocument.class));
        verify(mongo).insert(any(MongoStoryRevisionDocument.class));
    }

    @Test
    void conditionallyUpdatesOwnedEditableVersionBeforeRevisionInsert() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                org.mockito.ArgumentMatchers.eq(
                        MongoStoryDraftDocument.class
                )
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        MongoStoryDraftRepository repository =
                new MongoStoryDraftRepository(mongo);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        StoryDraft updated = new StoryDraft(
                "40000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                "truyen-mot-40000000",
                "Truyện Hai",
                "Tóm tắt",
                List.of("30000000-0000-4000-8000-000000000001"),
                StoryDraft.Origin.ORIGINAL,
                "vi",
                StoryDraft.CompletionStatus.COMPLETED,
                StoryDraft.WorkflowStatus.DRAFT,
                "50000000-0000-4000-8000-000000000002",
                null,
                now,
                now.plusSeconds(1),
                2
        );
        StoryRevision revision = new StoryRevision(
                updated.currentRevision(),
                updated.id(),
                2,
                new StoryRevision.Snapshot(
                        updated.title(),
                        updated.synopsis(),
                        updated.origin(),
                        updated.language(),
                        updated.categoryIds(),
                        null
                ),
                "10000000-0000-4000-8000-000000000001",
                "a".repeat(64),
                now.plusSeconds(1)
        );

        assertThat(repository.update(updated, revision, 1)).isTrue();

        verify(mongo).updateFirst(
                any(Query.class),
                any(Update.class),
                org.mockito.ArgumentMatchers.eq(
                        MongoStoryDraftDocument.class
                )
        );
        verify(mongo).insert(any(MongoStoryRevisionDocument.class));
    }
}
