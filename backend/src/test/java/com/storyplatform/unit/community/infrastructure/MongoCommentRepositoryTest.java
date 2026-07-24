package com.storyplatform.unit.community.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.community.infrastructure.persistence
        .MongoCommentRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoCommentRepositoryTest {

    @Test
    void ownerAndVersionArePartOfAtomicMutationCriteria() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(
                any(Query.class),
                any(UpdateDefinition.class),
                eq(MongoCommentRepository.COLLECTION)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        MongoCommentRepository repository =
                new MongoCommentRepository(mongo);

        assertThat(repository.updateOwned(
                "comment",
                "owner",
                7,
                "safe body",
                "fingerprint",
                Instant.EPOCH
        )).isTrue();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).updateFirst(
                query.capture(),
                any(UpdateDefinition.class),
                eq(MongoCommentRepository.COLLECTION)
        );
        Document criteria = query.getValue().getQueryObject();
        assertThat(criteria.toJson())
                .contains("\"_id\": \"comment\"")
                .contains("\"authorId\": \"owner\"")
                .contains("\"version\": 7");
    }

    @Test
    void batchesProfileLookupAndProvidesPrivacySafeFallback() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.find(
                any(Query.class),
                eq(MongoCommentRepository.Profile.class),
                eq("user_profiles")
        )).thenReturn(List.of());
        MongoCommentRepository repository =
                new MongoCommentRepository(mongo);

        assertThat(repository.authors(Set.of("one", "two")))
                .containsKeys("one", "two")
                .allSatisfy((id, author) -> {
                    assertThat(author.id()).isEqualTo(id);
                    assertThat(author.displayName()).isEqualTo("Độc giả");
                });
    }
}
