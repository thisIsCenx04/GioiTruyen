package com.storyplatform.unit.monetization.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoManualTopupAuditDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoManualTopupRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoPaymentEventDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoTopupRequestDocument;
import org.junit.jupiter.api.Test;
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

class MongoManualTopupRepositoryTest {

    @Test
    void persistsAuditOnlyAfterBothPendingCasUpdatesWin() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoTopupRequestDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoPaymentEventDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var repository = new MongoManualTopupRepository(mongo);

        assertThat(repository.complete(
                "topup",
                "event",
                "ledger",
                "admin",
                "Verified settlement statement",
                "evidence/bank-statement-1",
                Instant.EPOCH
        )).isTrue();
        verify(mongo).insert(any(MongoManualTopupAuditDocument.class));
    }

    @Test
    void returnsFalseWhenManualDecisionLosesToAutomation() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoTopupRequestDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 0L, null));

        assertThat(new MongoManualTopupRepository(mongo).complete(
                "topup",
                "event",
                "ledger",
                "admin",
                "Verified settlement statement",
                "evidence/bank-statement-1",
                Instant.EPOCH
        )).isFalse();
    }
}
