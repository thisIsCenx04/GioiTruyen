package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoLedgerRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoLedgerTransactionDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoLedgerRepositoryTest {

    @Test
    void roundTripsImmutableEmbeddedEntriesAndFindsIdempotency() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var transaction = transaction();
        when(mongo.insert(any(MongoLedgerTransactionDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mongo.findOne(
                any(Query.class),
                org.mockito.ArgumentMatchers.eq(
                        MongoLedgerTransactionDocument.class
                )
        )).thenAnswer(invocation -> {
            return captureInserted(mongo);
        });
        var repository = new MongoLedgerRepository(mongo);

        var inserted = repository.insert(transaction);

        assertThat(inserted).isEqualTo(transaction);
        assertThat(repository.findByIdempotencyKeyHash("a".repeat(64)))
                .contains(transaction);
        verify(mongo).findOne(
                any(Query.class),
                org.mockito.ArgumentMatchers.eq(
                        MongoLedgerTransactionDocument.class
                )
        );
    }

    private static MongoLedgerTransactionDocument captureInserted(
            MongoTemplate mongo
    ) {
        org.mockito.ArgumentCaptor<MongoLedgerTransactionDocument> captor =
                org.mockito.ArgumentCaptor.forClass(
                        MongoLedgerTransactionDocument.class
                );
        verify(mongo).insert(captor.capture());
        return captor.getValue();
    }

    private static LedgerTransaction transaction() {
        return LedgerTransaction.post(
                "10000000-0000-4000-8000-000000000001",
                LedgerTransaction.Type.DONATION,
                "donation",
                "donation-01",
                List.of(
                        new LedgerEntry(
                                "20000000-0000-4000-8000-000000000001",
                                LedgerEntry.Side.DEBIT,
                                100
                        ),
                        new LedgerEntry(
                                "30000000-0000-4000-8000-000000000001",
                                LedgerEntry.Side.CREDIT,
                                100
                        )
                ),
                "a".repeat(64),
                Instant.EPOCH
        );
    }
}
