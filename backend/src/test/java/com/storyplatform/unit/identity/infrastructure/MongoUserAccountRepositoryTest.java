package com.storyplatform.unit.identity.infrastructure;

import com.storyplatform.identity.domain.UserAccount;
import com.storyplatform.identity.infrastructure.persistence.MongoUserAccountDocument;
import com.storyplatform.identity.infrastructure.persistence.MongoUserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void successfulInsertReportsNewAccount() {
        when(mongoTemplate.insert(
                any(MongoUserAccountDocument.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(repository.saveIfEmailAvailable(account())).isTrue();
    }

    @Test
    void duplicateEmailIsHandledAsExistingAccount() {
        when(mongoTemplate.insert(
                any(MongoUserAccountDocument.class)
        )).thenThrow(new DuplicateKeyException("duplicate"));
        when(mongoTemplate.exists(
                any(Query.class),
                eq(MongoUserAccountDocument.COLLECTION)
        )).thenReturn(true);

        assertThat(repository.saveIfEmailAvailable(account())).isFalse();
    }

    @Test
    void unrelatedDuplicateKeyIsNotHidden() {
        DuplicateKeyException duplicate = new DuplicateKeyException(
                "id collision"
        );
        when(mongoTemplate.insert(
                any(MongoUserAccountDocument.class)
        )).thenThrow(duplicate);
        when(mongoTemplate.exists(
                any(Query.class),
                eq(MongoUserAccountDocument.COLLECTION)
        )).thenReturn(false);

        assertThatThrownBy(() ->
                repository.saveIfEmailAvailable(account())
        ).isSameAs(duplicate);
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
