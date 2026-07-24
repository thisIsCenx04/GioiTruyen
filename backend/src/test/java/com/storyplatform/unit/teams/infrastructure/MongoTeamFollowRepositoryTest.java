package com.storyplatform.unit.teams.infrastructure;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.storyplatform.teams.domain.TeamFollow;
import com.storyplatform.teams.infrastructure.persistence
        .MongoTeamFollowDocument;
import com.storyplatform.teams.infrastructure.persistence
        .MongoTeamFollowRepository;
import org.bson.BsonString;
import org.junit.jupiter.api.BeforeEach;
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

class MongoTeamFollowRepositoryTest {

    private MongoTemplate mongo;
    private MongoTeamFollowRepository repository;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        repository = new MongoTeamFollowRepository(mongo);
    }

    @Test
    void atomicUpsertReportsCreatedOnlyOnce() {
        when(mongo.upsert(
                any(Query.class),
                any(Update.class),
                eq(MongoTeamFollowDocument.class)
        )).thenReturn(
                UpdateResult.acknowledged(
                        0,
                        0L,
                        new BsonString("team-1:user-1")
                ),
                UpdateResult.acknowledged(1, 0L, null)
        );

        assertThat(repository.insertIfAbsent(follow())).isTrue();
        assertThat(repository.insertIfAbsent(follow())).isFalse();
    }

    @Test
    void deleteStatusAndCountUseOwnedRelationPredicates() {
        when(mongo.remove(
                any(Query.class),
                eq(MongoTeamFollowDocument.class)
        )).thenReturn(
                DeleteResult.acknowledged(1),
                DeleteResult.acknowledged(0)
        );
        when(mongo.exists(
                any(Query.class),
                eq(MongoTeamFollowDocument.class)
        )).thenReturn(true);
        when(mongo.count(
                any(Query.class),
                eq(MongoTeamFollowDocument.class)
        )).thenReturn(3L);

        assertThat(repository.deleteIfPresent("team-1", "user-1"))
                .isTrue();
        assertThat(repository.deleteIfPresent("team-1", "user-1"))
                .isFalse();
        assertThat(repository.exists("team-1", "user-1")).isTrue();
        assertThat(repository.count("team-1")).isEqualTo(3);
    }

    private static TeamFollow follow() {
        return TeamFollow.create(
                "team-1",
                "user-1",
                Instant.parse("2026-07-24T00:00:00Z")
        );
    }
}
