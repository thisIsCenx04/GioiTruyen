package com.storyplatform.unit.community.infrastructure;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.storyplatform.community.domain.StoryRelation;
import com.storyplatform.community.infrastructure.persistence
        .MongoStoryRelationRepository;
import org.bson.BsonString;
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

class MongoStoryRelationRepositoryTest {

    @Test
    void usesAtomicUniqueUpsertAndTypeScopedQueries() {
        var mongo = mock(MongoTemplate.class);
        when(mongo.exists(any(Query.class), eq("stories")))
                .thenReturn(true);
        when(mongo.upsert(
                any(Query.class),
                any(Update.class),
                eq(MongoStoryRelationRepository.RelationDocument.class),
                eq(MongoStoryRelationRepository.COLLECTION)
        )).thenReturn(
                UpdateResult.acknowledged(
                        0, 0L, new BsonString("created")
                ),
                UpdateResult.acknowledged(1, 0L, null)
        );
        when(mongo.remove(
                any(Query.class),
                eq(MongoStoryRelationRepository.COLLECTION)
        )).thenReturn(DeleteResult.acknowledged(1));
        when(mongo.exists(
                any(Query.class),
                eq(MongoStoryRelationRepository.COLLECTION)
        )).thenReturn(true);
        when(mongo.count(
                any(Query.class),
                eq(MongoStoryRelationRepository.COLLECTION)
        )).thenReturn(4L);
        var repository = new MongoStoryRelationRepository(mongo);
        var relation = StoryRelation.create(
                "story",
                "user",
                StoryRelation.Type.FAVORITE,
                Instant.EPOCH
        );

        assertThat(repository.storyIsPublished("story")).isTrue();
        assertThat(repository.insertIfAbsent(relation)).isTrue();
        assertThat(repository.insertIfAbsent(relation)).isFalse();
        assertThat(repository.deleteIfPresent(
                "story", "user", StoryRelation.Type.FAVORITE
        )).isTrue();
        assertThat(repository.exists(
                "story", "user", StoryRelation.Type.FAVORITE
        )).isTrue();
        assertThat(repository.count(
                "story", StoryRelation.Type.FAVORITE
        )).isEqualTo(4);
    }
}
