package com.storyplatform.unit.reading.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.reading.application.ReadingProgressOperations;
import com.storyplatform.reading.infrastructure.persistence
        .MongoReadingProgressRepository;
import com.storyplatform.reading.infrastructure.persistence
        .MongoReadingProgressRepository.ProgressDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoReadingProgressRepositoryTest {

    @Test
    void storesReadsAndCompareAndSetsPrivateProgress() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        String user = "10000000-0000-4000-8000-000000000001";
        String story = "20000000-0000-4000-8000-000000000001";
        String chapter = "30000000-0000-4000-8000-000000000001";
        var view = new ReadingProgressOperations.ProgressView(
                story, chapter, 42, now, now, 2
        );
        when(mongo.findOne(
                any(Query.class),
                eq(ProgressDocument.class),
                eq(MongoReadingProgressRepository.COLLECTION)
        )).thenReturn(new ProgressDocument(
                "progress",
                user,
                story,
                chapter,
                40,
                now.minusSeconds(1),
                now,
                1
        ));
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoReadingProgressRepository.COLLECTION)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var repository = new MongoReadingProgressRepository(mongo);

        assertThat(repository.find(user, story)).isPresent();
        assertThat(repository.create(user, view)).isTrue();
        assertThat(repository.update(
                user, view, 1, now.minusSeconds(1)
        )).isTrue();
    }

    @Test
    void requiresBothPublishedStoryAndOwnedChapter() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.exists(any(Query.class), eq("chapters")))
                .thenReturn(true);
        when(mongo.exists(any(Query.class), eq("stories")))
                .thenReturn(false);

        assertThat(new MongoReadingProgressRepository(mongo)
                .chapterIsPublished("story", "chapter")).isFalse();
    }
}
