package com.storyplatform.unit.identity.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.identity.domain.UserAccount;
import com.storyplatform.identity.infrastructure.persistence.MongoUserAccountDocument;
import com.storyplatform.identity.infrastructure.persistence.MongoUserAccountRepository;
import org.bson.BsonString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoUserAccountRepositoryTest {

    private MongoTemplate mongoTemplate;
    private MongoUserAccountRepository repository;

    @BeforeEach
    void configureRepository() {
        mongoTemplate = mock(MongoTemplate.class);
        repository = new MongoUserAccountRepository(mongoTemplate);
    }

    @Test
    void successfulAtomicUpsertReportsNewAccount() {
        when(mongoTemplate.upsert(
                any(Query.class),
                any(Update.class),
                org.mockito.ArgumentMatchers.eq(
                        MongoUserAccountDocument.class
                )
        )).thenReturn(UpdateResult.acknowledged(
                1L,
                0L,
                new BsonString("user-1")
        ));

        assertThat(repository.saveIfEmailAvailable(account())).isTrue();
    }

    @Test
    void existingEmailDoesNotOverwriteAccountOrAbortTransaction() {
        when(mongoTemplate.upsert(
                any(Query.class),
                any(Update.class),
                org.mockito.ArgumentMatchers.eq(
                        MongoUserAccountDocument.class
                )
        )).thenReturn(UpdateResult.acknowledged(1L, 0L, null));

        assertThat(repository.saveIfEmailAvailable(account())).isFalse();
    }

    private static UserAccount account() {
        return UserAccount.pending(
                "user-1",
                "reader@example.com",
                "$argon2id$hash",
                "2026-07-24",
                Instant.parse("2026-07-24T00:00:00Z")
        );
    }
}
