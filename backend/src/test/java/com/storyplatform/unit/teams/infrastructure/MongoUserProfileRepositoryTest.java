package com.storyplatform.unit.teams.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.teams.application.port.UserProfileRepository;
import com.storyplatform.teams.infrastructure.persistence.MongoUserProfileDocument;
import com.storyplatform.teams.infrastructure.persistence.MongoUserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoUserProfileRepositoryTest {

    private static final Instant NOW = Instant.parse(
            "2026-07-24T00:00:00Z"
    );
    private MongoTemplate mongo;
    private MongoUserProfileRepository repository;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        repository = new MongoUserProfileRepository(mongo);
    }

    @Test
    void atomicallyCreatesDefaultProfile() {
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoUserProfileDocument.class)
        )).thenReturn(document(0));

        assertThat(repository.findOrCreate(
                "user-1",
                "Độc giả",
                NOW
        ).displayName()).isEqualTo("Độc giả");
    }

    @Test
    void distinguishesUpdatedStaleAndMissingWrites() {
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoUserProfileDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        assertThat(update()).isEqualTo(
                UserProfileRepository.UpdateResult.UPDATED
        );

        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoUserProfileDocument.class)
        )).thenReturn(UpdateResult.acknowledged(0, 0L, null));
        when(mongo.exists(
                any(Query.class),
                eq(MongoUserProfileDocument.class)
        )).thenReturn(true, false);
        assertThat(update()).isEqualTo(
                UserProfileRepository.UpdateResult.VERSION_CONFLICT
        );
        assertThat(update()).isEqualTo(
                UserProfileRepository.UpdateResult.NOT_FOUND
        );
    }

    @Test
    void findsExistingProfileByUserId() {
        when(mongo.findById(
                "user-1",
                MongoUserProfileDocument.class
        )).thenReturn(document(2));

        assertThat(repository.findByUserId("user-1"))
                .get()
                .extracting(profile -> profile.version())
                .isEqualTo(2L);
    }

    private UserProfileRepository.UpdateResult update() {
        return repository.update(
                "user-1",
                0,
                "Lam Dạ",
                "",
                null,
                NOW
        );
    }

    private static MongoUserProfileDocument document(long version) {
        return new MongoUserProfileDocument(
                "user-1",
                "Độc giả",
                "",
                null,
                NOW,
                NOW,
                version
        );
    }
}
