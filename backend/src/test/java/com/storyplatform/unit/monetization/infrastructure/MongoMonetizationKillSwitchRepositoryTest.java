package com.storyplatform.unit.monetization.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.monetization.domain.MonetizationKillSwitch;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoMonetizationKillSwitchAuditDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoMonetizationKillSwitchDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoMonetizationKillSwitchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoMonetizationKillSwitchRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");

    @Test
    void insertsInitialStateAndImmutableAudit() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var repository = new MongoMonetizationKillSwitchRepository(mongo);

        assertThat(repository.save(
                state(false, 0),
                state(true, 1),
                "Incident containment approved."
        )).isTrue();
        verify(mongo).insert(any(
                MongoMonetizationKillSwitchDocument.class
        ));
        verify(mongo).insert(any(
                MongoMonetizationKillSwitchAuditDocument.class
        ));
    }

    @Test
    void duplicateInitialAndLostUpdateReturnFalse() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.insert(any(
                MongoMonetizationKillSwitchDocument.class
        ))).thenThrow(new DuplicateKeyException("race"));
        var repository = new MongoMonetizationKillSwitchRepository(mongo);

        assertThat(repository.save(
                state(false, 0),
                state(true, 1),
                "Incident containment approved."
        )).isFalse();

        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoMonetizationKillSwitchDocument.class)
        )).thenReturn(UpdateResult.acknowledged(0, 0L, null));
        assertThat(repository.save(
                state(true, 1),
                state(false, 2),
                "Incident recovery was approved."
        )).isFalse();
    }

    @Test
    void readsAndUpdatesVersionedState() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findById(
                MonetizationKillSwitch.Operation.TOPUP_CREDIT.name(),
                MongoMonetizationKillSwitchDocument.class
        )).thenReturn(new MongoMonetizationKillSwitchDocument(
                MonetizationKillSwitch.Operation.TOPUP_CREDIT.name(),
                true,
                1,
                "10000000-0000-4000-8000-000000000001",
                NOW
        ));
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoMonetizationKillSwitchDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var repository = new MongoMonetizationKillSwitchRepository(mongo);

        assertThat(repository.find(
                MonetizationKillSwitch.Operation.TOPUP_CREDIT
        )).contains(state(true, 1));
        assertThat(repository.save(
                state(true, 1),
                state(false, 2),
                "Incident recovery was approved."
        )).isTrue();
    }

    private static MonetizationKillSwitch state(
            boolean engaged,
            long version
    ) {
        return new MonetizationKillSwitch(
                MonetizationKillSwitch.Operation.TOPUP_CREDIT,
                engaged,
                version,
                "10000000-0000-4000-8000-000000000001",
                NOW
        );
    }
}
