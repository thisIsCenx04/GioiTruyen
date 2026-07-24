package com.storyplatform.unit.catalog.infrastructure;

import com.storyplatform.catalog.application.PublicChapterProjection;
import com.storyplatform.catalog.application.port.ChapterRepository;
import com.storyplatform.catalog.infrastructure.persistence
        .MongoChapterDocument;
import com.storyplatform.catalog.infrastructure.persistence
        .MongoChapterRepository;
import com.storyplatform.catalog.infrastructure.persistence
        .MongoChapterRepository.ChapterDetailDocument;
import com.storyplatform.catalog.infrastructure.persistence
        .MongoChapterRepository.ChapterRevisionDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoChapterRepositoryTest {

    @Test
    void forcesPublishedStatusAndUsesAContentFreeProjection() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.find(
                any(Query.class),
                eq(PublicChapterProjection.class),
                eq(MongoChapterDocument.COLLECTION)
        )).thenReturn(List.of());
        var repository = new MongoChapterRepository(mongo);

        repository.findPublished(new ChapterRepository.ChapterListQuery(
                "10000000-0000-4000-8000-000000000001",
                10,
                "20000000-0000-4000-8000-000000000001",
                21
        ));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(
                query.capture(),
                eq(PublicChapterProjection.class),
                eq(MongoChapterDocument.COLLECTION)
        );
        assertThat(query.getValue().getQueryObject().toString())
                .contains("workflowStatus=PUBLISHED")
                .contains("storyId=");
        assertThat(query.getValue().getFieldsObject().keySet())
                .contains("_id", "storyId", "number", "publishedAt")
                .doesNotContain(
                        "teamId",
                        "workflowStatus",
                        "currentRevision",
                        "scheduledAt",
                        "content"
                );
    }

    @Test
    void loadsTheExactCurrentRevisionAndPublishedNeighbors() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        String chapterId = "20000000-0000-4000-8000-000000000001";
        String storyId = "10000000-0000-4000-8000-000000000001";
        String revisionId = "30000000-0000-4000-8000-000000000001";
        when(mongo.findOne(
                any(Query.class),
                eq(ChapterDetailDocument.class),
                eq(MongoChapterDocument.COLLECTION)
        )).thenReturn(new ChapterDetailDocument(
                chapterId,
                storyId,
                2,
                "chapter-2",
                "Chapter 2",
                revisionId,
                Instant.parse("2026-07-24T00:00:00Z"),
                4
        ));
        when(mongo.findOne(
                any(Query.class),
                eq(ChapterRevisionDocument.class),
                eq("chapter_revisions")
        )).thenReturn(new ChapterRevisionDocument(
                revisionId,
                chapterId,
                3,
                "<p>Evidence</p>",
                "Evidence",
                "a".repeat(64)
        ));
        when(mongo.findOne(
                any(Query.class),
                eq(PublicChapterProjection.class),
                eq(MongoChapterDocument.COLLECTION)
        )).thenReturn(new PublicChapterProjection(
                "20000000-0000-4000-8000-000000000002",
                storyId,
                3,
                "chapter-3",
                "Chapter 3",
                Instant.parse("2026-07-24T00:00:00Z"),
                1
        ));
        var repository = new MongoChapterRepository(mongo);

        assertThat(repository.findPublishedDetail(chapterId))
                .get()
                .satisfies(stored -> {
                    assertThat(stored.revisionId()).isEqualTo(revisionId);
                    assertThat(stored.contentHtml())
                            .isEqualTo("<p>Evidence</p>");
                });
        assertThat(repository.previous(storyId, 2, chapterId)).isPresent();
        assertThat(repository.next(storyId, 2, chapterId)).isPresent();
    }

    @Test
    void neverFallsBackWhenChapterOrFrozenRevisionIsMissing() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var repository = new MongoChapterRepository(mongo);
        String chapterId = "20000000-0000-4000-8000-000000000001";

        assertThat(repository.findPublishedDetail(chapterId)).isEmpty();

        when(mongo.findOne(
                any(Query.class),
                eq(ChapterDetailDocument.class),
                eq(MongoChapterDocument.COLLECTION)
        )).thenReturn(new ChapterDetailDocument(
                chapterId,
                "10000000-0000-4000-8000-000000000001",
                1,
                "chapter-1",
                "Chapter",
                "30000000-0000-4000-8000-000000000001",
                Instant.parse("2026-07-24T00:00:00Z"),
                1
        ));

        assertThat(repository.findPublishedDetail(chapterId)).isEmpty();
    }
}
