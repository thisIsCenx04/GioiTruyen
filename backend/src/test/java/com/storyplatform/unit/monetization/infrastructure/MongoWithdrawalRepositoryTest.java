package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.application.WithdrawalException;
import com.storyplatform.monetization.application.port
        .WithdrawalCursorCodec;
import com.storyplatform.monetization.domain.Withdrawal;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWithdrawalDestinationDirectory;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWithdrawalDestinationDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWithdrawalDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWithdrawalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoWithdrawalRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");

    @Test
    void readsIdempotencyAndKeysetTeamHistory() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findOne(
                any(Query.class),
                eq(MongoWithdrawalDocument.class)
        )).thenReturn(document());
        when(mongo.find(
                any(Query.class),
                eq(MongoWithdrawalDocument.class)
        )).thenReturn(List.of(document()));
        var repository = new MongoWithdrawalRepository(mongo);

        assertThat(repository.findByIdempotencyKeyHash("hash"))
                .contains(withdrawal());
        assertThat(repository.findByTeam(
                withdrawal().teamId(),
                new WithdrawalCursorCodec.Position(
                        NOW.plusSeconds(1),
                        "70000000-0000-4000-8000-000000000001"
                ),
                21
        )).containsExactly(withdrawal());
        assertThat(repository.findByTeam(
                withdrawal().teamId(),
                null,
                21
        )).containsExactly(withdrawal());
    }

    @Test
    void duplicateInsertMapsToConflict() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.insert(any(MongoWithdrawalDocument.class)))
                .thenThrow(new DuplicateKeyException("race"));

        assertThatThrownBy(() ->
                new MongoWithdrawalRepository(mongo).insert(withdrawal())
        ).isInstanceOf(WithdrawalException.class)
                .extracting("kind")
                .isEqualTo(WithdrawalException.Kind.CONFLICT);
    }

    @Test
    void readsOnlyVerifiedAvailableDestinationOwnedByTeam() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findOne(
                any(Query.class),
                eq(MongoWithdrawalDestinationDocument.class)
        )).thenReturn(new MongoWithdrawalDestinationDocument(
                withdrawal().destination().id(),
                withdrawal().teamId(),
                withdrawal().destination().maskedLabel(),
                withdrawal().destination().encryptedPayload(),
                withdrawal().destination().version(),
                "VERIFIED",
                withdrawal().destination().verifiedAt(),
                NOW.minusSeconds(1)
        ));

        assertThat(new MongoWithdrawalDestinationDirectory(mongo)
                .findEligible(
                        withdrawal().teamId(),
                        withdrawal().destination().id(),
                        NOW
                )).contains(withdrawal().destination());
    }

    private static Withdrawal withdrawal() {
        return new Withdrawal(
                "10000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                "30000000-0000-4000-8000-000000000001",
                100_000,
                new Withdrawal.DestinationSnapshot(
                        "40000000-0000-4000-8000-000000000001",
                        "VCB •••• 1234",
                        "encrypted:v1:ciphertext",
                        1,
                        NOW.minusSeconds(60)
                ),
                Withdrawal.State.PENDING_REVIEW,
                "50000000-0000-4000-8000-000000000001",
                "60000000-0000-4000-8000-000000000001",
                "a".repeat(64),
                "b".repeat(64),
                NOW
        );
    }

    private static MongoWithdrawalDocument document() {
        Withdrawal value = withdrawal();
        return new MongoWithdrawalDocument(
                value.id(),
                value.teamId(),
                value.accountId(),
                value.grossAmountXu(),
                new MongoWithdrawalDocument.DestinationSnapshot(
                        value.destination().id(),
                        value.destination().maskedLabel(),
                        value.destination().encryptedPayload(),
                        value.destination().version(),
                        value.destination().verifiedAt()
                ),
                value.state().name(),
                value.requestedBy(),
                value.reserveTransactionId(),
                value.idempotencyKeyHash(),
                value.requestHash(),
                value.createdAt()
        );
    }
}
