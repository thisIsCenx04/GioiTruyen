package com.storyplatform.unit.monetization.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoPaymentEventDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoTopupRequestDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoTopupSettlementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoTopupSettlementRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T00:00:00Z");

    @Test
    void completesOnlyWhenBothAwaitingStatesWinTheirCas() {
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
        var repository = new MongoTopupSettlementRepository(mongo);

        assertThat(repository.complete(
                "event", "topup", "ledger", NOW
        )).isTrue();

        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoTopupRequestDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 0L, null));
        assertThat(repository.complete(
                "event", "topup", "ledger", NOW
        )).isFalse();
    }

    @Test
    void findsStoredEventAndTopupByProviderAndReference() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findOne(
                any(Query.class),
                eq(MongoPaymentEventDocument.class)
        )).thenReturn(payment());
        when(mongo.findOne(
                any(Query.class),
                eq(MongoTopupRequestDocument.class)
        )).thenReturn(topup());
        var repository = new MongoTopupSettlementRepository(mongo);

        assertThat(repository.findEvent("bank", "event-1")).isPresent();
        assertThat(repository.findTopup("GT12345678901234")).isPresent();
        assertThat(repository.findOldestReceived("bank")).isPresent();
    }

    @Test
    void flagsAnUnmatchedEventWithoutMutatingAnyTopup() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoPaymentEventDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var repository = new MongoTopupSettlementRepository(mongo);

        assertThat(repository.flagForReview(
                "event", null, "TOPUP_NOT_FOUND", NOW
        )).isTrue();
    }

    private static MongoPaymentEventDocument payment() {
        return new MongoPaymentEventDocument(
                "30000000-0000-4000-8000-000000000001",
                "bank",
                "event-1",
                "bank-ref",
                100_000,
                "GT12345678901234",
                NOW.minusSeconds(10),
                NOW,
                "a".repeat(64),
                "{}",
                "RECEIVED"
        );
    }

    private static MongoTopupRequestDocument topup() {
        return new MongoTopupRequestDocument(
                "20000000-0000-4000-8000-000000000001",
                "reader",
                100_000,
                90_000,
                BigDecimal.TEN,
                0,
                "GT12345678901234",
                "qr",
                "AWAITING_PAYMENT",
                NOW.plusSeconds(60),
                NOW.minusSeconds(60),
                "b".repeat(64),
                "c".repeat(64)
        );
    }
}
