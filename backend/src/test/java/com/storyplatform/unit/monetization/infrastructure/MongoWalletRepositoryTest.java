package com.storyplatform.unit.monetization.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.monetization.application
        .InsufficientWalletBalanceException;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWalletAccountDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWalletBalanceDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWalletRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoWalletRepositoryTest {

    private static final String ACCOUNT =
            "10000000-0000-4000-8000-000000000001";
    private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");

    @Test
    void atomicallyMovesAvailableXuToReservedWithoutGoingNegative() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findById(ACCOUNT, MongoWalletAccountDocument.class))
                .thenReturn(account());
        UpdateResult changed = mock(UpdateResult.class);
        when(changed.getModifiedCount()).thenReturn(1L);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoWalletBalanceDocument.class)
        )).thenReturn(changed);
        var repository = new MongoWalletRepository(mongo);

        repository.project(reservation(), NOW);

        ArgumentCaptor<Update> updates =
                ArgumentCaptor.forClass(Update.class);
        verify(mongo, times(2)).updateFirst(
                any(Query.class),
                updates.capture(),
                eq(MongoWalletBalanceDocument.class)
        );
        assertThat(updates.getAllValues().toString())
                .contains(
                        "\"availableXu\" : -100",
                        "\"reservedXu\" : 100",
                        "\"version\" : 1"
                );
    }

    @Test
    void rejectsDebitWhenAtomicBalanceGuardDoesNotMatch() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findById(ACCOUNT, MongoWalletAccountDocument.class))
                .thenReturn(account());
        UpdateResult unchanged = mock(UpdateResult.class);
        when(unchanged.getModifiedCount()).thenReturn(0L);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoWalletBalanceDocument.class)
        )).thenReturn(unchanged);
        var repository = new MongoWalletRepository(mongo);

        assertThatThrownBy(() -> repository.project(reservation(), NOW))
                .isInstanceOf(InsufficientWalletBalanceException.class);
    }

    @Test
    void readsOwnerProjectionAndFailsClosedWhenItIsMissing() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findOne(
                any(Query.class),
                eq(MongoWalletAccountDocument.class)
        )).thenReturn(account());
        when(mongo.findById(ACCOUNT, MongoWalletBalanceDocument.class))
                .thenReturn(
                        new MongoWalletBalanceDocument(
                                ACCOUNT, 90, 10, 2, NOW
                        ),
                        (MongoWalletBalanceDocument) null
                );
        var repository = new MongoWalletRepository(mongo);

        assertThat(repository.find(
                com.storyplatform.monetization.domain.WalletAccount
                        .OwnerType.USER,
                "user"
        )).get().extracting("availableXu", "reservedXu")
                .containsExactly(90L, 10L);
        assertThatThrownBy(() -> repository.find(
                com.storyplatform.monetization.domain.WalletAccount
                        .OwnerType.USER,
                "user"
        )).isInstanceOf(IllegalStateException.class);
    }

    private static LedgerTransaction reservation() {
        return LedgerTransaction.post(
                "20000000-0000-4000-8000-000000000001",
                LedgerTransaction.Type.WITHDRAWAL_RESERVE,
                "withdrawal",
                "withdrawal-01",
                List.of(
                        new LedgerEntry(
                                ACCOUNT,
                                LedgerEntry.Side.DEBIT,
                                LedgerEntry.Bucket.AVAILABLE,
                                100
                        ),
                        new LedgerEntry(
                                ACCOUNT,
                                LedgerEntry.Side.CREDIT,
                                LedgerEntry.Bucket.RESERVED,
                                100
                        )
                ),
                "a".repeat(64),
                NOW
        );
    }

    private static MongoWalletAccountDocument account() {
        return new MongoWalletAccountDocument(
                ACCOUNT,
                "USER",
                "user",
                "CREDIT",
                "ACTIVE",
                "XU",
                NOW
        );
    }
}
