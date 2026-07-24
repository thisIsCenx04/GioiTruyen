package com.storyplatform.unit.catalog.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.catalog.application.PublicStoryProjection;
import com.storyplatform.catalog.domain.Story;
import com.storyplatform.catalog.infrastructure.persistence
        .MongoStoryDocument;
import com.storyplatform.catalog.infrastructure.persistence
        .MongoStoryRepository;
import org.bson.BsonString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

class MongoStoryRepositoryTest {

    private MongoTemplate mongo;
    private MongoStoryRepository stories;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        stories = new MongoStoryRepository(mongo);
    }

    @Test
    void reportsGlobalSlugUniquenessWithoutAReadBeforeWrite() {
        when(mongo.upsert(
                any(Query.class),
                any(Update.class),
                eq(MongoStoryDocument.class)
        )).thenReturn(
                UpdateResult.acknowledged(
                        0,
                        0L,
                        new BsonString("story-1")
                ),
                UpdateResult.acknowledged(1, 0L, null)
        );

        assertThat(stories.insertIfSlugAvailable(story())).isTrue();
        assertThat(stories.insertIfSlugAvailable(story())).isFalse();
    }

    @Test
    void publishedLookupUsesAnAllowlistedProjection() {
        PublicStoryProjection projection = new PublicStoryProjection(
                story().id(),
                story().teamId(),
                story().slug(),
                story().title(),
                story().synopsis(),
                story().categoryIds(),
                story().origin(),
                story().language(),
                story().completionStatus(),
                story().publishedAt(),
                story().updatedAt(),
                story().version()
        );
        when(mongo.findOne(
                any(Query.class),
                eq(PublicStoryProjection.class),
                eq(MongoStoryDocument.COLLECTION)
        )).thenReturn(projection);

        assertThat(stories.findPublishedByIdOrSlug("nguoi-giu-den"))
                .contains(projection);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).findOne(
                query.capture(),
                eq(PublicStoryProjection.class),
                eq(MongoStoryDocument.COLLECTION)
        );
        assertThat(query.getValue().getFieldsObject().keySet())
                .contains(
                        "_id",
                        "teamId",
                        "slug",
                        "title",
                        "synopsis",
                        "categoryIds",
                        "publishedAt",
                        "version"
                )
                .doesNotContain(
                        "aliases",
                        "currentRevision",
                        "coverAssetId"
                );
    }

    private static Story story() {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        return new Story(
                "20000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000002",
                "nguoi-giu-den",
                "Người giữ đèn",
                List.of("Tên cũ"),
                "Một câu chuyện dài.",
                List.of("30000000-0000-4000-8000-000000000001"),
                Story.Origin.ORIGINAL,
                "vi",
                Story.CompletionStatus.ONGOING,
                Story.WorkflowStatus.PUBLISHED,
                "20000000-0000-4000-8000-000000000003",
                null,
                now,
                now,
                now,
                1
        );
    }
}
