package com.storyplatform.unit.publishing.infrastructure;

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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
}
