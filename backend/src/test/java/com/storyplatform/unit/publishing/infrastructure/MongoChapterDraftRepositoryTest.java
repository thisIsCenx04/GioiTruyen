package com.storyplatform.unit.publishing.infrastructure;

import com.storyplatform.publishing.domain.ChapterDraft;
import com.storyplatform.publishing.domain.ChapterRevision;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoChapterDraftDocument;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoChapterDraftRepository;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoChapterRevisionDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoChapterDraftRepositoryTest {

    @Test
    void checksNumberAndStoresChapterWithSeparateRevision() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.exists(any(Query.class), any(Class.class)))
                .thenReturn(true);
        MongoChapterDraftRepository repository =
                new MongoChapterDraftRepository(mongo);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        ChapterDraft chapter = new ChapterDraft(
                "60000000-0000-4000-8000-000000000001",
                "40000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                1,
                "chapter-1-start-60000000",
                "Start",
                ChapterDraft.WorkflowStatus.DRAFT,
                "70000000-0000-4000-8000-000000000001",
                1,
                now,
                now,
                1
        );
        ChapterRevision revision = new ChapterRevision(
                chapter.currentRevision(),
                chapter.id(),
                1,
                "<p>Hello</p>",
                "Hello",
                "a".repeat(64),
                "10000000-0000-4000-8000-000000000001",
                now
        );

        assertThat(repository.numberExists(chapter.storyId(), 1))
                .isTrue();
        repository.insert(chapter, revision);

        verify(mongo).insert(any(MongoChapterDraftDocument.class));
        verify(mongo).insert(any(MongoChapterRevisionDocument.class));
    }
}
