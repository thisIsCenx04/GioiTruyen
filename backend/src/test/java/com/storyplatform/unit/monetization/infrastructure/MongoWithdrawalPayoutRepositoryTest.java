package com.storyplatform.unit.monetization.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.monetization.application.WithdrawalException;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutCallbackRepository;
import com.storyplatform.monetization.domain.WithdrawalPayout;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWithdrawalPayoutCallbackDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWithdrawalPayoutCallbackRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWithdrawalPayoutDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWithdrawalPayoutRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoWithdrawalPayoutRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");

    @Test
    void insertsReadsClaimsAndAtomicallyUpdatesPayout() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var document = document();
        when(mongo.insert(any(MongoWithdrawalPayoutDocument.class)))
                .thenReturn(document);
        when(mongo.findById(
                payout().withdrawalId(),
                MongoWithdrawalPayoutDocument.class
        )).thenReturn(document);
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoWithdrawalPayoutDocument.class)
        )).thenReturn(document);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoWithdrawalPayoutDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var repository = new MongoWithdrawalPayoutRepository(mongo);

        assertThat(repository.insert(payout())).isEqualTo(payout());
        assertThat(repository.findByWithdrawalId(
                payout().withdrawalId()
        )).contains(payout());
        assertThat(repository.claimRetryable(
                NOW, NOW.plusSeconds(30)
        )).contains(payout());
        assertThat(repository.update(
                payout(),
                payout().retry("provider-001", null, NOW.plusSeconds(30))
        )).isTrue();
    }

    @Test
    void mapsPayoutDuplicateToConflict() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.insert(any(MongoWithdrawalPayoutDocument.class)))
                .thenThrow(new DuplicateKeyException("race"));

        assertThatThrownBy(() ->
                new MongoWithdrawalPayoutRepository(mongo).insert(payout())
        ).isInstanceOf(WithdrawalException.class)
                .extracting("kind")
                .isEqualTo(WithdrawalException.Kind.CONFLICT);
    }

    @Test
    void persistsCallbackReceiptAndMapsDuplicate() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var receipt = receipt();
        when(mongo.findOne(
                any(Query.class),
                eq(MongoWithdrawalPayoutCallbackDocument.class)
        )).thenReturn(new MongoWithdrawalPayoutCallbackDocument(
                receipt.id(),
                receipt.provider(),
                receipt.eventId(),
                receipt.withdrawalId(),
                receipt.requestHash(),
                receipt.receivedAt()
        ));
        var repository = new MongoWithdrawalPayoutCallbackRepository(mongo);

        assertThat(repository.find(
                receipt.provider(), receipt.eventId()
        )).contains(receipt);
        repository.insert(receipt);

        when(mongo.insert(any(
                MongoWithdrawalPayoutCallbackDocument.class
        ))).thenThrow(new DuplicateKeyException("race"));
        assertThatThrownBy(() -> repository.insert(receipt))
                .isInstanceOf(WithdrawalException.class)
                .extracting("kind")
                .isEqualTo(WithdrawalException.Kind.CONFLICT);
    }

    private static WithdrawalPayout payout() {
        return new WithdrawalPayout(
                "10000000-0000-4000-8000-000000000001",
                "bank-provider",
                "withdrawal:10000000-0000-4000-8000-000000000001",
                WithdrawalPayout.State.PROCESSING,
                2,
                "provider-001",
                null,
                NOW,
                null,
                NOW.minusSeconds(30),
                null,
                null,
                null
        );
    }

    private static MongoWithdrawalPayoutDocument document() {
        return new MongoWithdrawalPayoutDocument(
                "10000000-0000-4000-8000-000000000001",
                "bank-provider",
                "withdrawal:10000000-0000-4000-8000-000000000001",
                "PROCESSING",
                2,
                "provider-001",
                null,
                NOW,
                null,
                NOW.minusSeconds(30),
                null,
                null,
                null
        );
    }

    private static WithdrawalPayoutCallbackRepository.Receipt receipt() {
        return new WithdrawalPayoutCallbackRepository.Receipt(
                "20000000-0000-4000-8000-000000000001",
                "bank-provider",
                "event-001",
                "10000000-0000-4000-8000-000000000001",
                "a".repeat(64),
                NOW
        );
    }
}
